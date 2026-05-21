from __future__ import annotations

import math
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
    names = []
    for slot in obj.material_slots:
        if slot.material:
            names.append(slot.material.name)
    return ", ".join(names) if names else "-"


def has_emission_material(obj: bpy.types.Object) -> bool:
    for slot in obj.material_slots:
        material = slot.material
        if not material:
            continue
        name = material.name.lower()
        if "emiss" in name or "led" in name or "light" in name:
            return True
        if not material.use_nodes:
            continue
        for node in material.node_tree.nodes:
            if node.bl_idname == "ShaderNodeEmission":
                return True
            if node.bl_idname == "ShaderNodeBsdfPrincipled":
                emission_strength = node.inputs.get("Emission Strength")
                if emission_strength and emission_strength.default_value > 0:
                    return True
    return False


def main() -> None:
    bpy.ops.wm.open_mainfile(filepath=str(BLEND_PATH))
    print(f"BLEND={BLEND_PATH}")
    print("name|dims|center|min|max|emissive|materials")
    for obj in bpy.context.scene.objects:
        if obj.type != "MESH" or not obj.visible_get():
            continue
        min_bounds, max_bounds = world_bounds(obj)
        dimensions = tuple(max_bounds[index] - min_bounds[index] for index in range(3))
        center = tuple((min_bounds[index] + max_bounds[index]) / 2 for index in range(3))
        wide_flat = (
            dimensions[0] > 0.35
            and dimensions[2] > 0.18
            and dimensions[1] < 0.18
            and 0.25 < center[1] < 0.9
        )
        tall_thin_leg = (
            dimensions[1] > 0.35
            and dimensions[0] < 0.12
            and dimensions[2] < 0.12
            and 0.0 < center[1] < 0.8
        )
        desk_name_hint = any(
            token in obj.name.lower()
            for token in ["desk", "table", "laminate", "leg", "steel", "plane", "cylinder"]
        )
        if not (wide_flat or tall_thin_leg or desk_name_hint or has_emission_material(obj)):
            continue
        print(
            "|".join(
                [
                    obj.name,
                    format_vector(dimensions),
                    format_vector(center),
                    format_vector(min_bounds),
                    format_vector(max_bounds),
                    str(has_emission_material(obj)),
                    material_summary(obj),
                ]
            )
        )


if __name__ == "__main__":
    main()
