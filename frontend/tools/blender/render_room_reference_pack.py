import argparse
import json
import math
import os
from pathlib import Path

import bpy
from mathutils import Vector


HUB_ROOM_POSITION = (-0.9, -0.1, 0.34)
HUB_ROOM_SCALE = 7.0

HUB_CAMERA_PRESETS = {
    "overview": {
        "position": (2.45, 5.05, 4.75),
        "target": (-2.55, 2.42, -2.82),
    },
    "monitor": {
        "position": (-2.38, 3.42, -0.92),
        "target": (-2.38, 3.23, -4.12),
    },
    "workspace": {
        "position": (0.75, 4.65, 3.35),
        "target": (-4.75, 2.62, -1.95),
    },
    "printer": {
        "position": (-1.92, 3.42, 0.26),
        "target": (-3.82, 2.4, -3.05),
    },
}

REFERENCE_VISIBLE_OBJECT_GROUPS = {
    "room shell": ("room isometric", "turn on for world lighting"),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--resolution-x", type=int, default=1280)
    parser.add_argument("--resolution-y", type=int, default=720)
    parser.add_argument("--samples", type=int, default=64)
    parser.add_argument(
        "--views",
        default="overview,monitor,workspace,printer",
        help="Comma-separated view names from HUB_CAMERA_PRESETS.",
    )
    if "--" in os.sys.argv:
        return parser.parse_args(os.sys.argv[os.sys.argv.index("--") + 1 :])
    return parser.parse_args()


def object_text(obj: bpy.types.Object) -> str:
    material_names = " ".join(
        slot.material.name for slot in obj.material_slots if slot.material
    )
    collection_names = " ".join(collection.name for collection in obj.users_collection)
    return f"{obj.name} {material_names} {collection_names}".lower()


def matches_object_group(obj: bpy.types.Object, keywords: tuple[str, ...]) -> bool:
    text = object_text(obj)
    return all(keyword in text for keyword in keywords)


def ensure_reference_objects_visible() -> list[dict]:
    visible_objects = []
    missing_groups = []

    for group_name, keywords in REFERENCE_VISIBLE_OBJECT_GROUPS.items():
        matching_objects = [
            obj
            for obj in bpy.context.scene.objects
            if matches_object_group(obj, keywords)
        ]

        if not matching_objects:
            missing_groups.append(group_name)
            continue

        for obj in matching_objects:
            obj.hide_render = False
            obj.hide_viewport = False
            obj.hide_set(False)
            visible_objects.append(
                {
                    "group": group_name,
                    "hideRender": obj.hide_render,
                    "hideViewport": obj.hide_viewport,
                    "name": obj.name,
                    "visible": obj.visible_get(),
                }
            )

    if missing_groups:
        raise RuntimeError(
            "Missing required reference object groups: " + ", ".join(missing_groups)
        )

    return visible_objects


def three_to_blender_position(position: tuple[float, float, float]) -> Vector:
    x, y, z = position

    return Vector(
        (
            (x - HUB_ROOM_POSITION[0]) / HUB_ROOM_SCALE,
            -(z - HUB_ROOM_POSITION[2]) / HUB_ROOM_SCALE,
            (y - HUB_ROOM_POSITION[1]) / HUB_ROOM_SCALE,
        )
    )


def look_at(camera: bpy.types.Object, target: Vector) -> None:
    direction = target - camera.location
    camera.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def create_reference_camera() -> bpy.types.Object:
    camera_data = bpy.data.cameras.new("CODEX_WebReferenceCamera")
    camera = bpy.data.objects.new("CODEX_WebReferenceCamera", camera_data)
    bpy.context.collection.objects.link(camera)
    bpy.context.scene.camera = camera
    camera_data.sensor_fit = "VERTICAL"
    camera_data.angle = math.radians(64)
    camera_data.sensor_width = 32
    camera_data.clip_start = 0.01
    camera_data.clip_end = 100

    return camera


def configure_render(resolution_x: int, resolution_y: int, samples: int) -> None:
    scene = bpy.context.scene
    scene.render.resolution_x = resolution_x
    scene.render.resolution_y = resolution_y
    scene.render.film_transparent = False
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.engine = "CYCLES"
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True

    try:
        scene.cycles.device = "GPU"
    except Exception:
        scene.cycles.device = "CPU"


def render_view(
    camera: bpy.types.Object,
    output_dir: Path,
    view_name: str,
) -> dict:
    preset = HUB_CAMERA_PRESETS[view_name]
    blender_position = three_to_blender_position(preset["position"])
    blender_target = three_to_blender_position(preset["target"])
    camera.location = blender_position
    look_at(camera, blender_target)

    output_path = output_dir / f"blender-{view_name}.png"
    bpy.context.scene.render.filepath = str(output_path)
    bpy.ops.render.render(write_still=True)

    return {
        "blenderCameraPosition": [round(value, 6) for value in blender_position],
        "blenderCameraTarget": [round(value, 6) for value in blender_target],
        "output": str(output_path),
        "view": view_name,
        "webCameraPosition": list(preset["position"]),
        "webCameraTarget": list(preset["target"]),
    }


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    visible_objects = ensure_reference_objects_visible()
    configure_render(args.resolution_x, args.resolution_y, args.samples)
    camera = create_reference_camera()
    requested_views = [
        view_name.strip()
        for view_name in args.views.split(",")
        if view_name.strip()
    ]
    invalid_views = [
        view_name for view_name in requested_views if view_name not in HUB_CAMERA_PRESETS
    ]

    if invalid_views:
        raise RuntimeError(f"Unknown reference view(s): {', '.join(invalid_views)}")

    payload = {
        "blendFile": bpy.data.filepath,
        "renderEngine": bpy.context.scene.render.engine,
        "resolution": [args.resolution_x, args.resolution_y],
        "samples": args.samples,
        "forcedVisibleObjects": visible_objects,
        "viewTransform": bpy.context.scene.view_settings.view_transform,
        "views": [render_view(camera, output_dir, view_name) for view_name in requested_views],
    }
    metadata_path = output_dir / "blender-reference-metadata.json"
    metadata_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), "utf-8")
    print("CODEX_ROOM_REFERENCE_PACK_START")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    print("CODEX_ROOM_REFERENCE_PACK_END")


if __name__ == "__main__":
    main()
