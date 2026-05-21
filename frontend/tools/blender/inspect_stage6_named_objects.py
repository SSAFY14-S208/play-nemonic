from __future__ import annotations

import argparse
import os
from pathlib import Path

import bpy


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--blend-path", required=True)
    if "--" in os.sys.argv:
        return parser.parse_args(os.sys.argv[os.sys.argv.index("--") + 1 :])
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    bpy.ops.wm.open_mainfile(filepath=str(Path(args.blend_path)))
    for name in [
        "Panel",
        "Glass panel",
        "WEB_SIMPLE_BACK_WALL_FOR_BAKE",
        "WEB_SIMPLE_LEFT_WALL_FOR_BAKE",
        "WEB_SIMPLE_FLOOR_FOR_BAKE",
        "Acoustic wood_block.001",
    ]:
        obj = bpy.data.objects.get(name)
        print(
            f"{name}|exists={bool(obj)}|hideViewport={obj.hide_viewport if obj else None}|hideRender={obj.hide_render if obj else None}"
        )


if __name__ == "__main__":
    main()
