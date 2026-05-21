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
    parser.add_argument("--mode", choices=("analyze", "bake", "hybrid-bake"), default="analyze")
    parser.add_argument("--resolution", type=int, default=1024)
    parser.add_argument("--samples", type=int, default=64)
    parser.add_argument("--margin", type=int, default=12)
    parser.add_argument("--profile", choices=("receivers", "full"), default="receivers")
    parser.add_argument("--source-collection", default="")
    parser.add_argument("--output-glb", default="")
    parser.add_argument("--output-image", default="")
    parser.add_argument("--output-base-image", default="")
    parser.add_argument("--output-ao-image", default="")
    parser.add_argument("--ao-strength", type=float, default=0.32)
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


def collect_collection_meshes(collection_name: str) -> list[bpy.types.Object]:
    collection = bpy.data.collections.get(collection_name)
    if collection is None:
        raise RuntimeError(f"Missing source collection: {collection_name}")

    meshes: list[bpy.types.Object] = []
    seen_names: set[str] = set()

    def visit(target_collection: bpy.types.Collection) -> None:
        for obj in target_collection.objects:
            if obj.type != "MESH" or obj.name in seen_names:
                continue
            seen_names.add(obj.name)
            meshes.append(obj)
        for child_collection in target_collection.children:
            visit(child_collection)

    visit(collection)
    return meshes


def classify_meshes(
    profile: str,
    source_collection: str = "",
) -> tuple[list[bpy.types.Object], list[bpy.types.Object]]:
    bake_objects: list[bpy.types.Object] = []
    excluded_objects: list[bpy.types.Object] = []

    source_objects = (
        collect_collection_meshes(source_collection)
        if source_collection
        else list(bpy.context.scene.objects)
    )

    for obj in source_objects:
        if is_helper_object(obj):
            obj.hide_render = True
            obj.hide_viewport = True
            continue
        if source_collection:
            obj.hide_render = False
            obj.hide_viewport = False
            obj.hide_set(False)
        elif not is_visible_mesh(obj):
            continue
        if is_excluded_from_bake(obj):
            excluded_objects.append(obj)
        elif not source_collection and profile == "receivers" and not is_receiver_surface(obj):
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


def ensure_objects_renderable(objects: list[bpy.types.Object]) -> None:
    for collection in bpy.data.collections:
        collection.hide_render = False
        collection.hide_viewport = False

    for obj in objects:
        obj.hide_render = False
        obj.hide_viewport = False
        obj.hide_set(False)
    bpy.context.view_layer.update()


def isolate_bake_objects(objects: list[bpy.types.Object]) -> None:
    bake_object_names = {obj.name for obj in objects}
    for obj in bpy.context.scene.objects:
        if obj.type != "MESH":
            continue

        should_hide = obj.name not in bake_object_names
        obj.hide_render = should_hide
        obj.hide_viewport = should_hide
        obj.hide_set(should_hide)

    ensure_objects_renderable(objects)


def create_lightmap_uvs(bake_objects: list[bpy.types.Object]) -> None:
    ensure_objects_renderable(bake_objects)
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


def create_bake_image(resolution: int, suffix: str = "") -> bpy.types.Image:
    image_name = f"{BAKE_IMAGE_NAME_PREFIX}_{resolution}"
    if suffix:
        image_name = f"{image_name}_{suffix}"

    image = bpy.data.images.new(
        image_name,
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
            for node in list(node_tree.nodes):
                if node.name == "NEMONIC_BAKE_TARGET":
                    node_tree.nodes.remove(node)
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


def create_pbr_baked_material(image: bpy.types.Image, resolution: int) -> bpy.types.Material:
    material = bpy.data.materials.new(f"{BAKED_MATERIAL_NAME_PREFIX}_HYBRID_{resolution}")
    material.use_nodes = True
    node_tree = material.node_tree
    node_tree.nodes.clear()

    uv_node = node_tree.nodes.new("ShaderNodeUVMap")
    uv_node.uv_map = BAKE_UV_NAME
    texture_node = node_tree.nodes.new("ShaderNodeTexImage")
    texture_node.image = image
    texture_node.extension = "CLIP"
    texture_node.interpolation = "Smart"
    principled_node = node_tree.nodes.new("ShaderNodeBsdfPrincipled")
    output_node = node_tree.nodes.new("ShaderNodeOutputMaterial")

    if "Metallic" in principled_node.inputs:
        principled_node.inputs["Metallic"].default_value = 0.0
    if "Roughness" in principled_node.inputs:
        principled_node.inputs["Roughness"].default_value = 0.72

    node_tree.links.new(uv_node.outputs["UV"], texture_node.inputs["Vector"])
    node_tree.links.new(texture_node.outputs["Color"], principled_node.inputs["Base Color"])
    node_tree.links.new(principled_node.outputs["BSDF"], output_node.inputs["Surface"])
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


def export_glb(output_glb: Path, export_objects: list[bpy.types.Object] | None = None) -> None:
    output_glb.parent.mkdir(parents=True, exist_ok=True)
    if export_objects:
        select_objects(export_objects)
    bpy.ops.export_scene.gltf(
        filepath=str(output_glb),
        export_format="GLB",
        use_selection=bool(export_objects),
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
    isolate_bake_objects(bake_objects)
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
    export_glb(Path(args.output_glb), bake_objects)

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


def bake_to_image(
    bake_objects: list[bpy.types.Object],
    image: bpy.types.Image,
    bake_type: str,
    margin: int,
    pass_filter: set[str] | None = None,
) -> None:
    ensure_objects_renderable(bake_objects)
    assign_bake_target_nodes(bake_objects, image)
    select_objects(bake_objects)
    bake_options = {
        "type": bake_type,
        "margin": margin,
        "use_clear": True,
    }
    if pass_filter is not None:
        bake_options["pass_filter"] = pass_filter
    bpy.ops.object.bake(**bake_options)


def combine_base_and_ao_images(
    base_image: bpy.types.Image,
    ao_image: bpy.types.Image,
    resolution: int,
    ao_strength: float,
) -> bpy.types.Image:
    combined_image = bpy.data.images.new(
        f"{BAKE_IMAGE_NAME_PREFIX}_{resolution}_Hybrid",
        width=resolution,
        height=resolution,
        alpha=False,
        float_buffer=False,
    )
    combined_image.colorspace_settings.name = "sRGB"

    base_pixels = list(base_image.pixels[:])
    ao_pixels = list(ao_image.pixels[:])
    combined_pixels = [0.0] * len(base_pixels)
    clamped_ao_strength = max(0.0, min(ao_strength, 1.0))

    for pixel_index in range(0, len(base_pixels), 4):
        ao_value = (
            ao_pixels[pixel_index]
            + ao_pixels[pixel_index + 1]
            + ao_pixels[pixel_index + 2]
        ) / 3.0
        shadow_multiplier = 1.0 - clamped_ao_strength + clamped_ao_strength * ao_value

        combined_pixels[pixel_index] = base_pixels[pixel_index] * shadow_multiplier
        combined_pixels[pixel_index + 1] = base_pixels[pixel_index + 1] * shadow_multiplier
        combined_pixels[pixel_index + 2] = base_pixels[pixel_index + 2] * shadow_multiplier
        combined_pixels[pixel_index + 3] = 1.0

    combined_image.pixels.foreach_set(combined_pixels)
    combined_image.update()
    return combined_image


def bake_hybrid_room(args: argparse.Namespace, bake_objects: list[bpy.types.Object]) -> None:
    if not bake_objects:
        raise RuntimeError("No static objects found for hybrid room baking.")
    if not args.output_glb:
        raise RuntimeError("--output-glb is required in hybrid-bake mode.")
    if not args.output_image:
        raise RuntimeError("--output-image is required in hybrid-bake mode.")

    start_time = time.time()
    enable_cycles(args.samples)
    duplicate_bake_mesh_data(bake_objects)
    isolate_bake_objects(bake_objects)
    create_lightmap_uvs(bake_objects)

    base_image = create_bake_image(args.resolution, "BaseColor")
    ao_image = create_bake_image(args.resolution, "AO")

    print(
        f"CODEX_HYBRID_BASE_BAKE_START objects={len(bake_objects)} "
        f"resolution={args.resolution} samples={args.samples}"
    )
    bake_to_image(bake_objects, base_image, "DIFFUSE", args.margin, {"COLOR"})

    print(
        f"CODEX_HYBRID_AO_BAKE_START objects={len(bake_objects)} "
        f"resolution={args.resolution} samples={args.samples}"
    )
    bake_to_image(bake_objects, ao_image, "AO", args.margin)

    combined_image = combine_base_and_ao_images(
        base_image,
        ao_image,
        args.resolution,
        args.ao_strength,
    )
    output_image = Path(args.output_image)
    save_bake_image(combined_image, output_image, args.texture_format)

    if args.output_base_image:
        save_bake_image(base_image, Path(args.output_base_image), args.texture_format)
    if args.output_ao_image:
        save_bake_image(ao_image, Path(args.output_ao_image), args.texture_format)

    baked_material = create_pbr_baked_material(combined_image, args.resolution)
    replace_with_baked_material(bake_objects, baked_material)
    export_glb(Path(args.output_glb), bake_objects)

    print(
        "CODEX_HYBRID_BAKE_EXPORT_DONE "
        + json.dumps(
            {
                "outputGlb": args.output_glb,
                "outputImage": args.output_image,
                "aoStrength": args.ao_strength,
                "seconds": round(time.time() - start_time, 2),
            },
            ensure_ascii=False,
        )
    )


def main() -> None:
    args = parse_args()
    bake_objects, excluded_objects = classify_meshes(
        args.profile,
        args.source_collection,
    )
    print_analysis(bake_objects, excluded_objects)
    if args.mode == "bake":
        bake_room(args, bake_objects)
    if args.mode == "hybrid-bake":
        bake_hybrid_room(args, bake_objects)


if __name__ == "__main__":
    main()
