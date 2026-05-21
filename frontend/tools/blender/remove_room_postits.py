import argparse
import os
from pathlib import Path

import bpy


POSTIT_EXACT_OBJECT_NAMES = {
    "post_it_label",
}
POSTIT_OBJECT_KEYWORDS = (
    "pplane5_post_it",
)
RENDER_HELPER_OBJECT_KEYWORDS = (
    "room isometric",
    "room volumetric",
    "volumetric",
)
BROKEN_PAPER_MATERIAL_NAMES = {
    "paper_yellow_mango.001",
}
BROKEN_PAPER_MAX_DIMENSION = 5.0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-glb", required=True)
    parser.add_argument("--output-glb", required=True)
    if "--" in os.sys.argv:
        return parser.parse_args(os.sys.argv[os.sys.argv.index("--") + 1 :])
    return parser.parse_args()


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete()


def is_postit_object(obj: bpy.types.Object) -> bool:
    object_name = obj.name.lower()
    return (
        object_name in POSTIT_EXACT_OBJECT_NAMES
        or any(keyword in object_name for keyword in POSTIT_OBJECT_KEYWORDS)
    )


def is_render_helper_object(obj: bpy.types.Object) -> bool:
    object_name = obj.name.lower()
    return any(keyword in object_name for keyword in RENDER_HELPER_OBJECT_KEYWORDS)


def is_broken_export_paper_object(obj: bpy.types.Object) -> bool:
    if obj.type != "MESH":
        return False

    material_names = {
        slot.material.name.lower()
        for slot in obj.material_slots
        if slot.material
    }
    if not material_names.intersection(BROKEN_PAPER_MATERIAL_NAMES):
        return False

    world_dimensions = obj.dimensions
    return max(world_dimensions.x, world_dimensions.y, world_dimensions.z) > BROKEN_PAPER_MAX_DIMENSION


def remove_postits() -> list[str]:
    removed_names: list[str] = []
    for obj in list(bpy.context.scene.objects):
        if not (
            is_postit_object(obj)
            or is_render_helper_object(obj)
            or is_broken_export_paper_object(obj)
        ):
            continue
        removed_names.append(obj.name)
        bpy.data.objects.remove(obj, do_unlink=True)
    return removed_names


def export_scene(output_glb: Path) -> None:
    output_glb.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=str(output_glb),
        export_format="GLB",
        use_selection=False,
        export_apply=True,
        export_materials="EXPORT",
        export_yup=True,
        export_lights=False,
        export_cameras=False,
    )


def main() -> None:
    args = parse_args()
    input_glb = Path(args.input_glb)
    output_glb = Path(args.output_glb)

    clear_scene()
    bpy.ops.import_scene.gltf(filepath=str(input_glb))

    removed_names = remove_postits()
    if not removed_names:
        raise RuntimeError("No removable post-it objects found.")

    export_scene(output_glb)
    print(f"Removed post-it objects: {removed_names}, output={output_glb}")


if __name__ == "__main__":
    main()
