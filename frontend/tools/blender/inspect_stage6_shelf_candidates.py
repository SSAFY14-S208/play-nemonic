from __future__ import annotations

from pathlib import Path

import bpy
from mathutils import Vector


BLEND_PATH = Path(
    r"C:\Users\SSAFY\Desktop\망고슬래브\에셋\3D에셋\pink room\Room_web_bake_stage6_no_glass_static.blend"
)


def world_bounds(obj: bpy.types.Object) -> tuple[tuple[float, float, float], tuple[float, float, float]]:
    corners = [obj.matrix_world @ Vector(corner) for corner in obj.bound_box]
    min_bounds = tuple(min(corner[index] for corner in corners) for index in range(3))
    max_bounds = tuple(max(corner[index] for corner in corners) for index in range(3))
    return min_bounds, max_bounds


def format_vector(values: tuple[float, float, float]) -> str:
    return ",".join(f"{value:.3f}" for value in values)


def material_summary(obj: bpy.types.Object) -> str:
    material_names = [
        slot.material.name
        for slot in obj.material_slots
        if slot.material
    ]
    return ", ".join(material_names) if material_names else "-"


def main() -> None:
    bpy.ops.wm.open_mainfile(filepath=str(BLEND_PATH))
    print(f"BLEND={BLEND_PATH}")
    print("name|dims|center|min|max|materials")

    for obj in bpy.context.scene.objects:
        if obj.type != "MESH" or not obj.visible_get():
            continue

        min_bounds, max_bounds = world_bounds(obj)
        dimensions = tuple(max_bounds[index] - min_bounds[index] for index in range(3))
        center = tuple((min_bounds[index] + max_bounds[index]) / 2 for index in range(3))
        object_name = obj.name.lower()
        material_names = material_summary(obj).lower()

        shelf_area = (
            -0.76 < center[0] < -0.45
            and -0.18 < center[1] < 0.36
            and 0.32 < center[2] < 0.72
        )
        shelf_hint = any(
            token in object_name or token in material_names
            for token in ["shelf", "bookcase", "wood", "laminate", "gold metal"]
        )

        if not (shelf_area or shelf_hint):
            continue

        print(
            "|".join(
                [
                    obj.name,
                    format_vector(dimensions),
                    format_vector(center),
                    format_vector(min_bounds),
                    format_vector(max_bounds),
                    material_summary(obj),
                ]
            )
        )


if __name__ == "__main__":
    main()
