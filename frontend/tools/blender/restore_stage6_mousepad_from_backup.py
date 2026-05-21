from __future__ import annotations

import shutil
from pathlib import Path

import bpy


CURRENT_BLEND_PATH = Path(
    r"C:\Users\SSAFY\Desktop\망고슬래브\에셋\3D에셋\pink room\Room_web_bake_stage6_no_glass_static.blend"
)
SOURCE_BLEND_PATH = Path(
    r"C:\Users\SSAFY\Desktop\망고슬래브\에셋\3D에셋\pink room\Room_web_bake_stage6_no_glass_static.before_shelf_merge_20260518_0131.blend"
)
SAFETY_BACKUP_PATH = CURRENT_BLEND_PATH.with_name(
    "Room_web_bake_stage6_no_glass_static.before_mousepad_restore_20260518_0143.blend"
)
MOUSEPAD_OBJECT_NAME = "MousePAd"


def remove_current_mousepad() -> None:
    mousepad = bpy.data.objects.get(MOUSEPAD_OBJECT_NAME)
    if not mousepad:
        return

    mesh = mousepad.data
    bpy.data.objects.remove(mousepad, do_unlink=True)
    if mesh and mesh.users == 0:
        bpy.data.meshes.remove(mesh)


def append_source_mousepad() -> bpy.types.Object:
    with bpy.data.libraries.load(str(SOURCE_BLEND_PATH), link=False) as (source_data, target_data):
        if MOUSEPAD_OBJECT_NAME not in source_data.objects:
            raise RuntimeError(f"Missing source object: {MOUSEPAD_OBJECT_NAME}")
        target_data.objects = [MOUSEPAD_OBJECT_NAME]

    appended = bpy.data.objects.get(MOUSEPAD_OBJECT_NAME)
    if not appended:
        raise RuntimeError(f"Failed to append object: {MOUSEPAD_OBJECT_NAME}")

    bpy.context.collection.objects.link(appended)
    appended.hide_set(False)
    appended.hide_viewport = False
    appended.hide_render = False
    return appended


def main() -> None:
    if not SAFETY_BACKUP_PATH.exists():
        shutil.copy2(CURRENT_BLEND_PATH, SAFETY_BACKUP_PATH)

    bpy.ops.wm.open_mainfile(filepath=str(CURRENT_BLEND_PATH))
    remove_current_mousepad()
    restored = append_source_mousepad()
    bpy.ops.wm.save_as_mainfile(filepath=str(CURRENT_BLEND_PATH))

    materials = [
        slot.material.name if slot.material else None
        for slot in restored.material_slots
    ]
    print(f"backup={SAFETY_BACKUP_PATH}")
    print(f"restored={restored.name}")
    print(f"materials={materials}")


if __name__ == "__main__":
    main()
