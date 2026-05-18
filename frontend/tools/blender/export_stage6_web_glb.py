import argparse
import json
import os
from pathlib import Path

import bpy


EXPORTABLE_OBJECT_TYPES = {"MESH", "FONT", "CURVE"}
EXCLUDED_OBJECT_NAME_KEYWORDS = (
    "room isometric",
    "room volumetric",
    "volumetric",
    "glass panel",
    "pplane5_post_it",
    "post_it_label",
)
REQUIRED_OBJECT_NAMES = {
    "Mouse_Merged_Atlas",
    "Plant_Joined_SourceMaterials",
    "WEB_SIMPLE_BACK_WALL_FOR_BAKE",
    "WEB_SIMPLE_FLOOR_FOR_BAKE",
    "WEB_SIMPLE_LEFT_WALL_FOR_BAKE",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-glb", required=True)
    if "--" in os.sys.argv:
        return parser.parse_args(os.sys.argv[os.sys.argv.index("--") + 1 :])
    return parser.parse_args()


def is_excluded_object(obj: bpy.types.Object) -> bool:
    object_name = obj.name.lower()
    return any(keyword in object_name for keyword in EXCLUDED_OBJECT_NAME_KEYWORDS)


def is_exportable_object(obj: bpy.types.Object) -> bool:
    return (
        obj.type in EXPORTABLE_OBJECT_TYPES
        and not obj.hide_get()
        and not obj.hide_viewport
        and not obj.hide_render
        and not is_excluded_object(obj)
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


def collect_export_objects() -> list[bpy.types.Object]:
    export_objects: list[bpy.types.Object] = []
    for obj in bpy.context.scene.objects:
        if is_exportable_object(obj):
            export_objects.append(obj)
    return export_objects


def validate_export_objects(export_objects: list[bpy.types.Object]) -> None:
    export_object_names = {obj.name for obj in export_objects}
    missing_names = sorted(REQUIRED_OBJECT_NAMES - export_object_names)
    if missing_names:
        raise RuntimeError(f"Missing required export objects: {missing_names}")


def export_glb(export_objects: list[bpy.types.Object], output_glb: Path) -> None:
    output_glb.parent.mkdir(parents=True, exist_ok=True)
    if bpy.context.object:
        bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    for obj in export_objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = export_objects[0]
    bpy.ops.export_scene.gltf(
        filepath=str(output_glb),
        export_format="GLB",
        use_selection=True,
        export_cameras=False,
        export_lights=False,
        export_materials="EXPORT",
        export_yup=True,
    )


def print_export_summary(export_objects: list[bpy.types.Object], output_glb: Path) -> None:
    payload = {
        "outputGlb": str(output_glb),
        "objectCount": len(export_objects),
        "triangleCount": sum(triangle_count(obj) for obj in export_objects),
        "requiredObjects": {
            object_name: object_name in {obj.name for obj in export_objects}
            for object_name in sorted(REQUIRED_OBJECT_NAMES)
        },
        "topObjects": sorted(
            [
                {
                    "name": obj.name,
                    "triangles": triangle_count(obj),
                    "materials": [
                        slot.material.name
                        for slot in obj.material_slots
                        if slot.material
                    ],
                }
                for obj in export_objects
                if obj.type == "MESH"
            ],
            key=lambda item: item["triangles"],
            reverse=True,
        )[:30],
    }
    print("CODEX_STAGE6_WEB_EXPORT_START")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    print("CODEX_STAGE6_WEB_EXPORT_END")


def main() -> None:
    args = parse_args()
    output_glb = Path(args.output_glb)
    export_objects = collect_export_objects()
    if not export_objects:
        raise RuntimeError("No exportable objects found.")
    validate_export_objects(export_objects)
    print_export_summary(export_objects, output_glb)
    export_glb(export_objects, output_glb)


if __name__ == "__main__":
    main()
