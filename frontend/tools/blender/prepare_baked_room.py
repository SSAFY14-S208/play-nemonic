import argparse
import json
import os
import time
from pathlib import Path

import bpy


BAKE_UV_NAME = "NEMONIC_BakeUV"
BAKE_IMAGE_NAME_PREFIX = "NEMONIC_RoomBake"
BAKED_MATERIAL_NAME_PREFIX = "NEMONIC_BAKED_ROOM"

EXCLUDE_KEYWORDS = (
    "display",
    "emissive",
    "fan",
    "label",
    "led",
    "logo",
    "nemonic",
    "output",
    "phone",
    "printer",
    "rgb",
    "screen",
    "text",
)

HELPER_KEYWORDS = (
    "gltf_not_exported",
    "helper",
    "light probe",
    "room volumetric",
    "turn on for world lighting",
    "volumetric",
)

RECEIVER_KEYWORDS = (
    "acoustic",
    "board",
    "carpet",
    "cube shelf",
    "curtain",
    "desk",
    "floor",
    "leg",
    "laminate sheet",
    "panel",
    "pegboard",
    "pegboards",
    "shelf",
    "table",
    "table legs",
    "table tops",
    "tabletop",
    "wall",
    "white carpet",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("analyze", "bake"), default="analyze")
    parser.add_argument("--resolution", type=int, default=1024)
    parser.add_argument("--samples", type=int, default=64)
    parser.add_argument("--margin", type=int, default=12)
    parser.add_argument("--profile", choices=("receivers", "full"), default="receivers")
    parser.add_argument("--output-glb", default="")
    parser.add_argument("--output-image", default="")
    parser.add_argument("--bake-type", choices=("COMBINED", "AO"), default="COMBINED")
    parser.add_argument("--texture-format", choices=("PNG", "JPEG"), default="PNG")
    if "--" in os.sys.argv:
        return parser.parse_args(os.sys.argv[os.sys.argv.index("--") + 1 :])
    return parser.parse_args([])


def normalized_object_text(obj: bpy.types.Object) -> str:
    material_names = " ".join(
        slot.material.name for slot in obj.material_slots if slot.material
    )
    collection_names = " ".join(collection.name for collection in obj.users_collection)
    return f"{obj.name} {material_names} {collection_names}".lower()


def is_helper_object(obj: bpy.types.Object) -> bool:
    text = normalized_object_text(obj)
    return any(keyword in text for keyword in HELPER_KEYWORDS)


def is_excluded_from_bake(obj: bpy.types.Object) -> bool:
    text = normalized_object_text(obj)
    return any(keyword in text for keyword in EXCLUDE_KEYWORDS)


def is_visible_mesh(obj: bpy.types.Object) -> bool:
    return (
        obj.type == "MESH"
        and not obj.hide_get()
        and not obj.hide_viewport
        and not obj.hide_render
    )


def is_receiver_surface(obj: bpy.types.Object) -> bool:
    text = normalized_object_text(obj)
    return any(keyword in text for keyword in RECEIVER_KEYWORDS)


def classify_meshes(profile: str) -> tuple[list[bpy.types.Object], list[bpy.types.Object]]:
    bake_objects: list[bpy.types.Object] = []
    excluded_objects: list[bpy.types.Object] = []

    for obj in bpy.context.scene.objects:
        if is_helper_object(obj):
            obj.hide_render = True
            obj.hide_viewport = True
            continue
        if not is_visible_mesh(obj):
            continue
        if is_excluded_from_bake(obj):
            excluded_objects.append(obj)
        elif profile == "receivers" and not is_receiver_surface(obj):
            excluded_objects.append(obj)
        else:
            bake_objects.append(obj)

    return bake_objects, excluded_objects


def triangle_count(obj: bpy.types.Object) -> int:
    return sum(max(1, len(poly.vertices) - 2) for poly in obj.data.polygons)


def print_analysis(bake_objects: list[bpy.types.Object], excluded_objects: list[bpy.types.Object]) -> None:
    lights = [
        {
            "name": obj.name,
            "type": obj.data.type,
            "energy": getattr(obj.data, "energy", None),
            "hidden": obj.hide_get() or obj.hide_viewport or obj.hide_render,
        }
        for obj in bpy.context.scene.objects
        if obj.type == "LIGHT"
    ]
    meshes = [
        {
            "name": obj.name,
            "triangles": triangle_count(obj),
            "materials": [slot.material.name for slot in obj.material_slots if slot.material],
            "collection": obj.users_collection[0].name if obj.users_collection else "",
        }
        for obj in bake_objects + excluded_objects
    ]
    payload = {
        "scene": bpy.context.scene.name,
        "renderEngine": bpy.context.scene.render.engine,
        "viewTransform": bpy.context.scene.view_settings.view_transform,
        "look": bpy.context.scene.view_settings.look,
        "objectCount": len(bpy.context.scene.objects),
        "materialCount": len(bpy.data.materials),
        "lightCount": len(lights),
        "meshCount": len(meshes),
        "bakeCandidateCount": len(bake_objects),
        "excludedCandidateCount": len(excluded_objects),
        "bakeTriangleCount": sum(triangle_count(obj) for obj in bake_objects),
        "excludedTriangleCount": sum(triangle_count(obj) for obj in excluded_objects),
        "lights": lights,
        "topBakeMeshes": sorted(
            meshes[: len(bake_objects)], key=lambda item: item["triangles"], reverse=True
        )[:40],
        "excludedMeshes": meshes[len(bake_objects) :],
    }
    print("CODEX_BAKE_ANALYSIS_START")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    print("CODEX_BAKE_ANALYSIS_END")


def enable_cycles(samples: int) -> None:
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    scene.render.bake.target = "IMAGE_TEXTURES"
    try:
        preferences = bpy.context.preferences.addons["cycles"].preferences
        preferences.get_devices()
        for device in preferences.devices:
            device.use = True
        scene.cycles.device = "GPU"
    except Exception as error:
        print(f"CODEX_BAKE_GPU_FALLBACK {error}")


def duplicate_bake_mesh_data(bake_objects: list[bpy.types.Object]) -> None:
    for obj in bake_objects:
        obj.data = obj.data.copy()


def select_objects(objects: list[bpy.types.Object]) -> None:
    bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = objects[0]


def create_lightmap_uvs(bake_objects: list[bpy.types.Object]) -> None:
    for obj in bake_objects:
        if BAKE_UV_NAME not in obj.data.uv_layers:
            obj.data.uv_layers.new(name=BAKE_UV_NAME)
        uv_layer = obj.data.uv_layers[BAKE_UV_NAME]
        obj.data.uv_layers.active = uv_layer
        uv_layer.active_render = True

    select_objects(bake_objects)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.lightmap_pack(
        PREF_CONTEXT="ALL_FACES",
        PREF_PACK_IN_ONE=True,
        PREF_NEW_UVLAYER=False,
        PREF_BOX_DIV=12,
        PREF_MARGIN_DIV=0.025,
    )
    bpy.ops.object.mode_set(mode="OBJECT")


def ensure_material(obj: bpy.types.Object) -> bpy.types.Material:
    if obj.material_slots and obj.material_slots[0].material:
        return obj.material_slots[0].material
    material = bpy.data.materials.new(f"{obj.name}_BakeSource")
    material.use_nodes = True
    obj.data.materials.append(material)
    return material


def create_bake_image(resolution: int) -> bpy.types.Image:
    image = bpy.data.images.new(
        f"{BAKE_IMAGE_NAME_PREFIX}_{resolution}",
        width=resolution,
        height=resolution,
        alpha=False,
        float_buffer=False,
    )
    image.colorspace_settings.name = "sRGB"
    return image


def assign_bake_target_nodes(
    bake_objects: list[bpy.types.Object], image: bpy.types.Image
) -> None:
    seen_materials: set[bpy.types.Material] = set()
    for obj in bake_objects:
        if not obj.material_slots:
            ensure_material(obj)
        for slot in obj.material_slots:
            material = slot.material or ensure_material(obj)
            if material in seen_materials:
                continue
            seen_materials.add(material)
            material.use_nodes = True
            node_tree = material.node_tree
            texture_node = node_tree.nodes.new("ShaderNodeTexImage")
            texture_node.name = "NEMONIC_BAKE_TARGET"
            texture_node.label = "NEMONIC Bake Target"
            texture_node.image = image
            texture_node.select = True
            node_tree.nodes.active = texture_node


def create_unlit_baked_material(image: bpy.types.Image, resolution: int) -> bpy.types.Material:
    material = bpy.data.materials.new(f"{BAKED_MATERIAL_NAME_PREFIX}_{resolution}")
    material.use_nodes = True
    node_tree = material.node_tree
    node_tree.nodes.clear()

    uv_node = node_tree.nodes.new("ShaderNodeUVMap")
    uv_node.uv_map = BAKE_UV_NAME
    texture_node = node_tree.nodes.new("ShaderNodeTexImage")
    texture_node.image = image
    texture_node.extension = "CLIP"
    texture_node.interpolation = "Smart"
    emission_node = node_tree.nodes.new("ShaderNodeEmission")
    emission_node.inputs["Strength"].default_value = 1.0
    output_node = node_tree.nodes.new("ShaderNodeOutputMaterial")

    node_tree.links.new(uv_node.outputs["UV"], texture_node.inputs["Vector"])
    node_tree.links.new(texture_node.outputs["Color"], emission_node.inputs["Color"])
    node_tree.links.new(emission_node.outputs["Emission"], output_node.inputs["Surface"])
    return material


def replace_with_baked_material(
    bake_objects: list[bpy.types.Object],
    baked_material: bpy.types.Material,
) -> None:
    for obj in bake_objects:
        obj.data.materials.clear()
        obj.data.materials.append(baked_material)
        while len(obj.data.uv_layers) > 1:
            for uv_layer in obj.data.uv_layers:
                if uv_layer.name != BAKE_UV_NAME:
                    obj.data.uv_layers.remove(uv_layer)
                    break
        obj.data.uv_layers.active = obj.data.uv_layers[BAKE_UV_NAME]
        obj.data.uv_layers[BAKE_UV_NAME].active_render = True


def save_bake_image(image: bpy.types.Image, output_image: Path, texture_format: str) -> None:
    output_image.parent.mkdir(parents=True, exist_ok=True)
    image.filepath_raw = str(output_image)
    image.file_format = texture_format
    image.save()


def export_glb(output_glb: Path) -> None:
    output_glb.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.object.select_all(action="DESELECT")
    bpy.ops.export_scene.gltf(
        filepath=str(output_glb),
        export_format="GLB",
        export_cameras=False,
        export_lights=False,
        export_materials="EXPORT",
        export_yup=True,
    )


def bake_room(args: argparse.Namespace, bake_objects: list[bpy.types.Object]) -> None:
    if not bake_objects:
        raise RuntimeError("No static objects found for room baking.")
    if not args.output_glb:
        raise RuntimeError("--output-glb is required in bake mode.")
    if not args.output_image:
        raise RuntimeError("--output-image is required in bake mode.")

    start_time = time.time()
    enable_cycles(args.samples)
    duplicate_bake_mesh_data(bake_objects)
    create_lightmap_uvs(bake_objects)
    image = create_bake_image(args.resolution)
    assign_bake_target_nodes(bake_objects, image)
    select_objects(bake_objects)

    print(
        f"CODEX_BAKE_START objects={len(bake_objects)} "
        f"resolution={args.resolution} samples={args.samples} type={args.bake_type}"
    )
    bpy.ops.object.bake(type=args.bake_type, margin=args.margin, use_clear=True)
    print(f"CODEX_BAKE_DONE seconds={round(time.time() - start_time, 2)}")

    output_image = Path(args.output_image)
    save_bake_image(image, output_image, args.texture_format)
    baked_material = create_unlit_baked_material(image, args.resolution)
    replace_with_baked_material(bake_objects, baked_material)
    export_glb(Path(args.output_glb))

    print(
        "CODEX_BAKE_EXPORT_DONE "
        + json.dumps(
            {
                "outputGlb": args.output_glb,
                "outputImage": args.output_image,
                "seconds": round(time.time() - start_time, 2),
            },
            ensure_ascii=False,
        )
    )


def main() -> None:
    args = parse_args()
    bake_objects, excluded_objects = classify_meshes(args.profile)
    print_analysis(bake_objects, excluded_objects)
    if args.mode == "bake":
        bake_room(args, bake_objects)


if __name__ == "__main__":
    main()
