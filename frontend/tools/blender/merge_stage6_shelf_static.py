from __future__ import annotations

import shutil
from pathlib import Path

import bpy


BLEND_PATH = Path(
    r"C:\Users\SSAFY\Desktop\망고슬래브\에셋\3D에셋\pink room\Room_web_bake_stage6_no_glass_static.blend"
)
BACKUP_PATH = BLEND_PATH.with_name(
    "Room_web_bake_stage6_no_glass_static.before_shelf_merge_20260518_0131.blend"
)

SHELF_OBJECT_NAMES = [
    "Cube Shelf",
    "Shelf 1",
]
SHELF_MERGED_NAME = "Shelf_Body_Merged_Static"
SHELF_MATERIAL_NAME = "Shelf_Soft_White"


def get_or_create_shelf_material() -> bpy.types.Material:
    material = bpy.data.materials.get(SHELF_MATERIAL_NAME)
    if material:
        return material

    material = bpy.data.materials.new(SHELF_MATERIAL_NAME)
    material.use_nodes = True
    material.diffuse_color = (0.93, 0.91, 0.95, 1.0)

    principled = material.node_tree.nodes.get("Principled BSDF")
    if principled:
        principled.inputs["Base Color"].default_value = (0.93, 0.91, 0.95, 1.0)
        principled.inputs["Metallic"].default_value = 0.0
        principled.inputs["Roughness"].default_value = 0.62

    return material


def remove_existing_merged_shelf() -> None:
    existing = bpy.data.objects.get(SHELF_MERGED_NAME)
    if not existing:
        return

    mesh = existing.data
    bpy.data.objects.remove(existing, do_unlink=True)
    if mesh and mesh.users == 0:
        bpy.data.meshes.remove(mesh)


def join_shelf_objects() -> bpy.types.Object:
    shelf_objects = [bpy.data.objects.get(name) for name in SHELF_OBJECT_NAMES]
    missing_names = [
        name for name, shelf_object in zip(SHELF_OBJECT_NAMES, shelf_objects) if shelf_object is None
    ]
    if missing_names:
        raise RuntimeError(f"Missing shelf objects: {missing_names}")

    for obj in bpy.context.scene.objects:
        obj.select_set(False)

    active_object = shelf_objects[0]
    bpy.context.view_layer.objects.active = active_object
    for obj in shelf_objects:
        obj.hide_set(False)
        obj.hide_viewport = False
        obj.hide_render = False
        obj.select_set(True)

    bpy.ops.object.join()
    merged_object = bpy.context.object
    merged_object.name = SHELF_MERGED_NAME
    merged_object.data.name = f"{SHELF_MERGED_NAME}_Mesh"

    material = get_or_create_shelf_material()
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
    remove_existing_merged_shelf()
    merged_object = join_shelf_objects()
    bpy.ops.wm.save_as_mainfile(filepath=str(BLEND_PATH))

    print(f"backup={BACKUP_PATH}")
    print(f"merged={merged_object.name}")
    print("preserved=CommunityCanvasWhiteboard,CommunityCanvasWhiteboarOutline,Plant_Joined_SourceMaterials,Marker case")


if __name__ == "__main__":
    main()
