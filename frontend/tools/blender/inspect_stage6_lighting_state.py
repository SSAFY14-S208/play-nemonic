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
    blend_path = Path(args.blend_path)
    bpy.ops.wm.open_mainfile(filepath=str(blend_path))

    scene = bpy.context.scene
    world = scene.world
    print(f"BLEND={blend_path}")
    print(f"RENDER_ENGINE={scene.render.engine}")
    print(f"VIEW_TRANSFORM={scene.view_settings.view_transform}")
    print(f"LOOK={scene.view_settings.look}")
    print(f"EXPOSURE={scene.view_settings.exposure}")
    print(f"GAMMA={scene.view_settings.gamma}")
    print(f"WORLD_COLOR={tuple(round(value, 4) for value in world.color) if world else None}")

    print("LIGHTS_START")
    for obj in bpy.context.scene.objects:
        if obj.type != "LIGHT":
            continue
        light = obj.data
        print(
            "|".join(
                [
                    obj.name,
                    light.type,
                    f"energy={getattr(light, 'energy', None)}",
                    f"color={tuple(round(value, 4) for value in getattr(light, 'color', []))}",
                    f"hideViewport={obj.hide_viewport}",
                    f"hideRender={obj.hide_render}",
                ]
            )
        )
    print("LIGHTS_END")


if __name__ == "__main__":
    main()
