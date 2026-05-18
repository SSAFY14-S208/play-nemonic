from __future__ import annotations

import argparse
import os
from pathlib import Path

import bpy


CHECK_OBJECT_NAMES = [
    "MousePAd",
    "Mouse_Merged_Atlas",
    "Plant_Joined_SourceMaterials",
    "Desk_Table_Legs_Merged_Static",
    "Shelf_Body_Merged_Static",
    "Table Light strip",
    "WEB_SIMPLE_BACK_WALL_FOR_BAKE",
    "WEB_SIMPLE_FLOOR_FOR_BAKE",
    "WEB_SIMPLE_LEFT_WALL_FOR_BAKE",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--blend-path", required=True)
    if "--" in os.sys.argv:
        return parser.parse_args(os.sys.argv[os.sys.argv.index("--") + 1 :])
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    blend_path = Path(args.blend_path)
    bpy.ops.wm.open_mainfile(filepath=str(blend_path))
    print(f"BLEND={blend_path.name}")
    for object_name in CHECK_OBJECT_NAMES:
        print(f"{object_name}={bool(bpy.data.objects.get(object_name))}")

    mousepad = bpy.data.objects.get("MousePAd")
    if mousepad:
        materials = [
            slot.material.name if slot.material else None
            for slot in mousepad.material_slots
        ]
        print(f"MousePAd.materials={materials}")


if __name__ == "__main__":
    main()
