import argparse
import os
from pathlib import Path

import bpy


WALL_OBJECT_NAMES = {
    "WEB_SIMPLE_FLOOR_FOR_BAKE",
    "WEB_SIMPLE_BACK_WALL_FOR_BAKE",
    "WEB_SIMPLE_LEFT_WALL_FOR_BAKE",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-glb", required=True)
    parser.add_argument("--wall-source-blend", required=True)
    parser.add_argument("--output-glb", required=True)
    if "--" in os.sys.argv:
        return parser.parse_args(os.sys.argv[os.sys.argv.index("--") + 1 :])
    return parser.parse_args()


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete()


def remove_existing_wall_objects() -> None:
    for obj in list(bpy.context.scene.objects):
        if obj.name in WALL_OBJECT_NAMES:
            bpy.data.objects.remove(obj, do_unlink=True)


def append_wall_objects(source_blend: Path) -> list[str]:
    object_directory = source_blend / "Object"
    with bpy.data.libraries.load(str(source_blend), link=False) as (source, target):
        available_names = set(source.objects)
        missing_names = sorted(WALL_OBJECT_NAMES - available_names)
        if missing_names:
            raise RuntimeError(f"Missing wall objects in source blend: {missing_names}")
        target.objects = sorted(WALL_OBJECT_NAMES)

    appended_names: list[str] = []
    for obj in target.objects:
        if not obj:
            continue
        bpy.context.collection.objects.link(obj)
        obj.hide_set(False)
        obj.hide_viewport = False
        obj.hide_render = False
        appended_names.append(obj.name)

    return appended_names


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
    source_blend = Path(args.wall_source_blend)
    output_glb = Path(args.output_glb)

    clear_scene()
    bpy.ops.import_scene.gltf(filepath=str(input_glb))
    remove_existing_wall_objects()
    appended_names = append_wall_objects(source_blend)
    export_scene(output_glb)

    print(f"Appended stage6 wall objects: {appended_names}, output={output_glb}")


if __name__ == "__main__":
    main()
