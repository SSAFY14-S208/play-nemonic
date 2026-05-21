from __future__ import annotations

import argparse
import math
import os
from pathlib import Path

import bpy
from mathutils import Vector


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--blend-path", required=True)
    if "--" in os.sys.argv:
        return parser.parse_args(os.sys.argv[os.sys.argv.index("--") + 1 :])
    return parser.parse_args()


def rounded_tuple(values, digits: int = 4) -> tuple[float, ...]:
    return tuple(round(float(value), digits) for value in values)


def euler_degrees(obj: bpy.types.Object) -> tuple[float, float, float]:
    return tuple(round(math.degrees(angle), 2) for angle in obj.rotation_euler)


def world_bounds(obj: bpy.types.Object) -> tuple[tuple[float, float, float], tuple[float, float, float]]:
    corners = [obj.matrix_world @ Vector(corner) for corner in obj.bound_box]
    min_bounds = tuple(min(corner[index] for corner in corners) for index in range(3))
    max_bounds = tuple(max(corner[index] for corner in corners) for index in range(3))
    return rounded_tuple(min_bounds), rounded_tuple(max_bounds)


def object_materials(obj: bpy.types.Object) -> str:
    material_names = [
        slot.material.name
        for slot in obj.material_slots
        if slot.material
    ]
    return ",".join(material_names) if material_names else "-"


def collection_names(obj: bpy.types.Object) -> str:
    names = [collection.name for collection in obj.users_collection]
    return ",".join(names) if names else "-"


def print_scene_settings(blend_path: Path) -> None:
    scene = bpy.context.scene
    print("SCENE")
    print(f"blend={blend_path}")
    print(f"renderEngine={scene.render.engine}")
    print(f"viewTransform={scene.view_settings.view_transform}")
    print(f"look={scene.view_settings.look}")
    print(f"exposure={scene.view_settings.exposure}")
    print(f"gamma={scene.view_settings.gamma}")
    print(f"worldColor={rounded_tuple(scene.world.color) if scene.world else None}")


def print_lights() -> None:
    print("LIGHTS")
    for obj in sorted(
        [scene_object for scene_object in bpy.context.scene.objects if scene_object.type == "LIGHT"],
        key=lambda light_object: light_object.name,
    ):
        light = obj.data
        payload = {
            "name": obj.name,
            "type": light.type,
            "loc": rounded_tuple(obj.location),
            "rotDeg": euler_degrees(obj),
            "scale": rounded_tuple(obj.scale),
            "energy": round(float(getattr(light, "energy", 0.0)), 4),
            "color": rounded_tuple(getattr(light, "color", [])),
            "size": round(float(getattr(light, "size", 0.0)), 4) if hasattr(light, "size") else None,
            "sizeX": round(float(getattr(light, "size", 0.0)), 4) if hasattr(light, "size") else None,
            "sizeY": round(float(getattr(light, "size_y", 0.0)), 4) if hasattr(light, "size_y") else None,
            "hideViewport": obj.hide_viewport,
            "hideRender": obj.hide_render,
            "collections": collection_names(obj),
        }
        print(payload)


def is_wall_or_room_surface(obj: bpy.types.Object) -> bool:
    object_name = obj.name.lower()
    material_names = object_materials(obj).lower()
    keywords = [
        "wall",
        "panel",
        "acoustic",
        "wood_block",
        "web_simple",
        "floor",
        "room shell",
    ]
    return any(keyword in object_name or keyword in material_names for keyword in keywords)


def print_wall_surfaces() -> None:
    print("WALL_SURFACES")
    for obj in sorted(
        [
            scene_object
            for scene_object in bpy.context.scene.objects
            if scene_object.type == "MESH" and is_wall_or_room_surface(scene_object)
        ],
        key=lambda mesh_object: mesh_object.name,
    ):
        min_bounds, max_bounds = world_bounds(obj)
        print(
            {
                "name": obj.name,
                "loc": rounded_tuple(obj.location),
                "dims": rounded_tuple(obj.dimensions),
                "min": min_bounds,
                "max": max_bounds,
                "materials": object_materials(obj),
                "hideViewport": obj.hide_viewport,
                "hideRender": obj.hide_render,
                "collections": collection_names(obj),
            }
        )


def main() -> None:
    args = parse_args()
    blend_path = Path(args.blend_path)
    bpy.ops.wm.open_mainfile(filepath=str(blend_path))
    print_scene_settings(blend_path)
    print_lights()
    print_wall_surfaces()


if __name__ == "__main__":
    main()
