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
    "turn on for world lighting",
    "volumetric",
)

EXPORTABLE_OBJECT_TYPES = {"MESH", "FONT", "CURVE"}


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


def is_exportable_mesh(obj: bpy.types.Object) -> bool:
    return (
        obj.type in EXPORTABLE_OBJECT_TYPES
        and not obj.hide_get()
        and not obj.hide_viewport
        and not obj.hide_render
        and not is_helper_object(obj)
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
        if is_helper_object(obj):
            obj.hide_render = True
            obj.hide_viewport = True
        if is_exportable_mesh(obj):
            meshes.append(obj)
    return meshes


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
    print_analysis(meshes)
    if args.mode == "export":
        if not args.output_glb:
            raise RuntimeError("--output-glb is required in export mode.")
        decimate_panel_if_requested(meshes, args.panel_ratio)
        export_glb(meshes, Path(args.output_glb))


if __name__ == "__main__":
    main()
