import argparse
import json
import os
from pathlib import Path

import bpy


HELPER_KEYWORDS = (
    "gltf_not_exported",
    "helper",
    "light probe",
    "room volumetric",
    "volumetric",
)

RETIRED_ROOM_PROP_OBJECT_NAMES = {
    "cube top shelf",
    "cube topshelf",
    "frame.001",
    "large frame",
    "peg",
    "picture.001",
}
RETIRED_ROOM_PROP_OBJECT_KEYWORDS = (
    "cube top shelf",
    "cube topshelf",
    "large frame",
)
RETIRED_ROOM_PROP_COLLECTION_KEYWORDS = (
    "pegboards",
)

EXPORTABLE_OBJECT_TYPES = {"MESH", "FONT", "CURVE"}
REQUIRED_EXPORT_OBJECT_GROUPS = {
    "room shell": ("room isometric", "turn on for world lighting"),
    "main wall panel": ("panel",),
}

WEB_FALLBACK_MATERIAL_GROUPS = {
    "room shell": {
        "keywords": ("room isometric", "turn on for world lighting"),
        "material": "Nemonic_Web_Room_Shell_Fallback",
        "base_color": (0.92, 0.88, 1.0, 1.0),
        "roughness": 0.78,
    },
    "main wall panel": {
        "object_name": "panel",
        "material": "Nemonic_Web_Main_Wall_Panel_Fallback",
        "base_color": (0.86, 0.81, 0.98, 1.0),
        "roughness": 0.5,
        "replace_existing": True,
    },
    "cube shelf": {
        "object_name": "cube shelf",
        "material": "Nemonic_Web_Bright_Surface_Fallback",
        "base_color": (1.0, 0.97, 1.0, 1.0),
        "roughness": 0.38,
        "replace_existing": True,
    },
    "main tabletop": {
        "object_name": "main tabletop",
        "material": "Nemonic_Web_Bright_Surface_Fallback",
        "base_color": (1.0, 0.97, 1.0, 1.0),
        "roughness": 0.38,
        "replace_existing": True,
    },
    "side tabletop 001": {
        "object_name": "side tabletop.001",
        "material": "Nemonic_Web_Bright_Surface_Fallback",
        "base_color": (1.0, 0.97, 1.0, 1.0),
        "roughness": 0.38,
        "replace_existing": True,
    },
    "side tabletop 002": {
        "object_name": "side tabletop.002",
        "material": "Nemonic_Web_Bright_Surface_Fallback",
        "base_color": (1.0, 0.97, 1.0, 1.0),
        "roughness": 0.38,
        "replace_existing": True,
    },
    "carpet": {
        "object_name": "carpet",
        "material": "Nemonic_Web_Carpet_Fallback",
        "base_color": (1.0, 0.96, 1.0, 1.0),
        "roughness": 0.98,
        "replace_existing": True,
    },
    "shelf shell": {
        "object_name": "shelf 1",
        "material": "Nemonic_Web_Shelf_Fallback",
        "base_color": (0.98, 0.95, 1.0, 1.0),
        "roughness": 0.5,
    },
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("analyze", "export"), default="analyze")
    parser.add_argument("--output-glb", default="")
    parser.add_argument("--panel-ratio", type=float, default=1.0)
    if "--" in os.sys.argv:
        return parser.parse_args(os.sys.argv[os.sys.argv.index("--") + 1 :])
    return parser.parse_args([])


def object_text(obj: bpy.types.Object) -> str:
    material_names = " ".join(
        slot.material.name for slot in obj.material_slots if slot.material
    )
    collection_names = " ".join(collection.name for collection in obj.users_collection)
    return f"{obj.name} {material_names} {collection_names}".lower()


def is_helper_object(obj: bpy.types.Object) -> bool:
    text = object_text(obj)
    return any(keyword in text for keyword in HELPER_KEYWORDS)


def is_retired_room_prop(obj: bpy.types.Object) -> bool:
    object_name = obj.name.lower()
    collection_names = [collection.name.lower() for collection in obj.users_collection]

    return (
        object_name in RETIRED_ROOM_PROP_OBJECT_NAMES
        or any(keyword in object_name for keyword in RETIRED_ROOM_PROP_OBJECT_KEYWORDS)
        or any(
            collection_keyword in collection_name
            for collection_name in collection_names
            for collection_keyword in RETIRED_ROOM_PROP_COLLECTION_KEYWORDS
        )
    )


def matches_required_object_group(obj: bpy.types.Object, keywords: tuple[str, ...]) -> bool:
    text = object_text(obj)
    return all(keyword in text for keyword in keywords)


def is_required_export_object(obj: bpy.types.Object) -> bool:
    return any(
        matches_required_object_group(obj, keywords)
        for keywords in REQUIRED_EXPORT_OBJECT_GROUPS.values()
    )


def is_exportable_mesh(obj: bpy.types.Object) -> bool:
    return (
        obj.type in EXPORTABLE_OBJECT_TYPES
        and not obj.hide_get()
        and not obj.hide_viewport
        and (not obj.hide_render or is_required_export_object(obj))
        and not is_helper_object(obj)
        and not is_retired_room_prop(obj)
    )


def triangle_count(obj: bpy.types.Object) -> int:
    if obj.type == "MESH":
        return sum(max(1, len(poly.vertices) - 2) for poly in obj.data.polygons)

    depsgraph = bpy.context.evaluated_depsgraph_get()
    evaluated = obj.evaluated_get(depsgraph)
    mesh = evaluated.to_mesh()
    try:
        return sum(max(1, len(poly.vertices) - 2) for poly in mesh.polygons)
    finally:
        evaluated.to_mesh_clear()


def collect_export_meshes() -> list[bpy.types.Object]:
    meshes: list[bpy.types.Object] = []
    for obj in bpy.context.scene.objects:
        is_required_object = is_required_export_object(obj)

        if is_required_object:
            obj.hide_render = False
            obj.hide_viewport = False
            obj.hide_set(False)
        elif is_helper_object(obj) or is_retired_room_prop(obj):
            obj.hide_render = True
            obj.hide_viewport = True

        if is_exportable_mesh(obj):
            meshes.append(obj)
    return meshes


def validate_required_export_objects(meshes: list[bpy.types.Object]) -> None:
    missing_groups = []
    for group_name, keywords in REQUIRED_EXPORT_OBJECT_GROUPS.items():
        if not any(matches_required_object_group(obj, keywords) for obj in meshes):
            missing_groups.append(group_name)

    if missing_groups:
        raise RuntimeError(
            "Missing required room export objects: " + ", ".join(missing_groups)
        )


def get_or_create_fallback_material(
    material_name: str,
    base_color: tuple[float, float, float, float],
    roughness: float,
) -> bpy.types.Material:
    material = bpy.data.materials.get(material_name)
    if not material:
        material = bpy.data.materials.new(material_name)

    material.diffuse_color = base_color
    material.use_nodes = True
    principled = material.node_tree.nodes.get("Principled BSDF")

    if principled:
        principled.inputs["Base Color"].default_value = base_color
        principled.inputs["Metallic"].default_value = 0
        principled.inputs["Roughness"].default_value = roughness

    return material


def ensure_web_fallback_materials(meshes: list[bpy.types.Object]) -> None:
    for obj in meshes:
        if obj.type != "MESH":
            continue

        for material_group in WEB_FALLBACK_MATERIAL_GROUPS.values():
            object_name = material_group.get("object_name")
            keywords = material_group.get("keywords")

            if object_name and obj.name.lower() != object_name:
                continue

            if keywords and not matches_required_object_group(obj, keywords):
                continue

            if obj.data.materials and not material_group.get("replace_existing"):
                continue

            material = get_or_create_fallback_material(
                material_group["material"],
                material_group["base_color"],
                material_group["roughness"],
            )
            if material_group.get("replace_existing"):
                obj.data.materials.clear()
            obj.data.materials.append(material)
            break


def print_analysis(meshes: list[bpy.types.Object]) -> None:
    payload = {
        "scene": bpy.context.scene.name,
        "renderEngine": bpy.context.scene.render.engine,
        "viewTransform": bpy.context.scene.view_settings.view_transform,
        "look": bpy.context.scene.view_settings.look,
        "objectCount": len(bpy.context.scene.objects),
        "meshCount": len(meshes),
        "materialCount": len(bpy.data.materials),
        "lightCount": sum(1 for obj in bpy.context.scene.objects if obj.type == "LIGHT"),
        "retiredSkippedObjects": sorted(
            obj.name for obj in bpy.context.scene.objects if is_retired_room_prop(obj)
        ),
        "exportTriangleCount": sum(triangle_count(obj) for obj in meshes),
        "topMeshes": sorted(
            [
                {
                    "name": obj.name,
                    "triangles": triangle_count(obj),
                    "collection": obj.users_collection[0].name
                    if obj.users_collection
                    else "",
                    "materials": [
                        slot.material.name
                        for slot in obj.material_slots
                        if slot.material
                    ],
                }
                for obj in meshes
            ],
            key=lambda item: item["triangles"],
            reverse=True,
        )[:50],
    }
    print("CODEX_ROOM_EXPORT_ANALYSIS_START")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    print("CODEX_ROOM_EXPORT_ANALYSIS_END")


def decimate_panel_if_requested(meshes: list[bpy.types.Object], panel_ratio: float) -> None:
    if panel_ratio >= 1:
        return
    for obj in meshes:
        if obj.name.lower() != "panel":
            continue
        obj.data = obj.data.copy()
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.select_all(action="DESELECT")
        obj.select_set(True)
        modifier = obj.modifiers.new("NEMONIC_Web_Panel_Decimate", "DECIMATE")
        modifier.ratio = max(0.01, min(panel_ratio, 1.0))
        bpy.ops.object.modifier_apply(modifier=modifier.name)
        print(
            "CODEX_PANEL_DECIMATED "
            + json.dumps(
                {
                    "name": obj.name,
                    "ratio": panel_ratio,
                    "triangles": triangle_count(obj),
                },
                ensure_ascii=False,
            )
        )
        return


def export_glb(meshes: list[bpy.types.Object], output_glb: Path) -> None:
    if not meshes:
        raise RuntimeError("No exportable meshes found.")
    output_glb.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    for obj in meshes:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.export_scene.gltf(
        filepath=str(output_glb),
        export_format="GLB",
        use_selection=True,
        export_cameras=False,
        export_lights=False,
        export_materials="EXPORT",
        export_yup=True,
    )
    print(
        "CODEX_ROOM_EXPORT_DONE "
        + json.dumps({"outputGlb": str(output_glb)}, ensure_ascii=False)
    )


def main() -> None:
    args = parse_args()
    meshes = collect_export_meshes()
    validate_required_export_objects(meshes)
    ensure_web_fallback_materials(meshes)
    print_analysis(meshes)
    if args.mode == "export":
        if not args.output_glb:
            raise RuntimeError("--output-glb is required in export mode.")
        decimate_panel_if_requested(meshes, args.panel_ratio)
        export_glb(meshes, Path(args.output_glb))


if __name__ == "__main__":
    main()
