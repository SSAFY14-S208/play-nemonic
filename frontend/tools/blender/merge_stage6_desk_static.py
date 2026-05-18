from __future__ import annotations

import shutil
from pathlib import Path

import bpy


BLEND_PATH = Path(
    r"C:\Users\SSAFY\Desktop\망고슬래브\에셋\3D에셋\pink room\Room_web_bake_stage6_no_glass_static.blend"
)
BACKUP_PATH = BLEND_PATH.with_name(
    "Room_web_bake_stage6_no_glass_static.before_desk_merge_20260518_0127.blend"
)

DESK_OBJECT_NAMES = [
    "Main TableTop",
    "Side TableTop.001",
    "Side TableTop.002",
    "Sinlge Leg",
    "Back leg",
    "Back leg.002",
]
DESK_MERGED_NAME = "Desk_Table_Legs_Merged_Static"
DESK_MATERIAL_NAME = "Desk_Table_Legs_Soft_White"
LIGHT_STRIP_NAME = "Table Light strip"


def get_or_create_desk_material() -> bpy.types.Material:
    material = bpy.data.materials.get(DESK_MATERIAL_NAME)
    if material:
        return material

    material = bpy.data.materials.new(DESK_MATERIAL_NAME)
    material.use_nodes = True
    principled = material.node_tree.nodes.get("Principled BSDF")
    if principled:
        principled.inputs["Base Color"].default_value = (0.86, 0.84, 0.88, 1.0)
        principled.inputs["Metallic"].default_value = 0.0
        principled.inputs["Roughness"].default_value = 0.58
    return material


def remove_existing_merged_desk() -> None:
    existing = bpy.data.objects.get(DESK_MERGED_NAME)
    if not existing:
        return

    mesh = existing.data
    bpy.data.objects.remove(existing, do_unlink=True)
    if mesh and mesh.users == 0:
        bpy.data.meshes.remove(mesh)


def join_desk_objects() -> bpy.types.Object:
    desk_objects = [bpy.data.objects.get(name) for name in DESK_OBJECT_NAMES]
    missing_names = [
        name for name, desk_object in zip(DESK_OBJECT_NAMES, desk_objects) if desk_object is None
    ]
    if missing_names:
        raise RuntimeError(f"Missing desk objects: {missing_names}")

    for obj in bpy.context.scene.objects:
        obj.select_set(False)

    active_object = desk_objects[0]
    bpy.context.view_layer.objects.active = active_object
    for obj in desk_objects:
        obj.select_set(True)

    bpy.ops.object.join()
    merged_object = bpy.context.object
    merged_object.name = DESK_MERGED_NAME
    merged_object.data.name = f"{DESK_MERGED_NAME}_Mesh"

    material = get_or_create_desk_material()
    merged_object.data.materials.clear()
    merged_object.data.materials.append(material)
    for polygon in merged_object.data.polygons:
        polygon.material_index = 0

    merged_object.select_set(False)
    return merged_object


def main() -> None:
    if not BACKUP_PATH.exists():
        shutil.copy2(BLEND_PATH, BACKUP_PATH)

    bpy.ops.wm.open_mainfile(filepath=str(BLEND_PATH))
    remove_existing_merged_desk()

    light_strip = bpy.data.objects.get(LIGHT_STRIP_NAME)
    if light_strip:
        light_strip.hide_viewport = False
        light_strip.hide_render = False

    merged_object = join_desk_objects()
    bpy.ops.wm.save_as_mainfile(filepath=str(BLEND_PATH))
    print(f"backup={BACKUP_PATH}")
    print(f"merged={merged_object.name}")
    print(f"light_strip_preserved={bool(bpy.data.objects.get(LIGHT_STRIP_NAME))}")


if __name__ == "__main__":
    main()
