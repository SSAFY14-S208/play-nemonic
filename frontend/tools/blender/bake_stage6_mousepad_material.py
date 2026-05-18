from __future__ import annotations

import shutil
from pathlib import Path

import bpy


BLEND_PATH = Path(
    r"C:\Users\SSAFY\Desktop\망고슬래브\에셋\3D에셋\pink room\Room_web_bake_stage6_no_glass_static.blend"
)
BACKUP_PATH = BLEND_PATH.with_name(
    "Room_web_bake_stage6_no_glass_static.before_mousepad_bake_20260518_0137.blend"
)

MOUSEPAD_OBJECT_NAME = "MousePAd"
BASE_IMAGE_NAME = "MousePad_BaseColor_Baked"
EMISSIVE_IMAGE_NAME = "MousePad_Emissive_Baked"
FINAL_MATERIAL_NAME = "MousePad_Baked_Base_Emissive"
ATLAS_SIZE = 1024


def get_principled_node(material: bpy.types.Material) -> bpy.types.Node | None:
    if not material.use_nodes or not material.node_tree:
        return None
    return material.node_tree.nodes.get("Principled BSDF")


def configure_bake_settings() -> None:
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 96
    scene.cycles.use_denoising = False
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.view_settings.exposure = 0
    scene.view_settings.gamma = 1


def ensure_mousepad_uv(obj: bpy.types.Object) -> None:
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj

    if not obj.data.uv_layers:
        obj.data.uv_layers.new(name="MousePadBakeUV")
    obj.data.uv_layers.active = obj.data.uv_layers[0]

    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(
        angle_limit=0.35,
        island_margin=0.025,
        area_weight=0.0,
        correct_aspect=True,
        scale_to_bounds=True,
    )
    bpy.ops.object.mode_set(mode="OBJECT")


def create_bake_image(name: str) -> bpy.types.Image:
    old_image = bpy.data.images.get(name)
    if old_image:
        bpy.data.images.remove(old_image)
    image = bpy.data.images.new(name, ATLAS_SIZE, ATLAS_SIZE, alpha=True)
    image.generated_color = (0, 0, 0, 0)
    return image


def select_bake_target_node(material: bpy.types.Material, image: bpy.types.Image) -> None:
    material.use_nodes = True
    image_node = material.node_tree.nodes.new(type="ShaderNodeTexImage")
    image_node.name = f"{image.name}_BakeTarget"
    image_node.image = image
    material.node_tree.nodes.active = image_node

    for node in material.node_tree.nodes:
        node.select = False
    image_node.select = True


def prepare_bake_target(obj: bpy.types.Object, image: bpy.types.Image) -> None:
    for slot in obj.material_slots:
        if slot.material:
            select_bake_target_node(slot.material, image)

    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj


def bake_base_color(obj: bpy.types.Object, image: bpy.types.Image) -> None:
    prepare_bake_target(obj, image)
    bpy.ops.object.bake(type="DIFFUSE", pass_filter={"COLOR"}, margin=16)


def bake_emissive(obj: bpy.types.Object, image: bpy.types.Image) -> None:
    prepare_bake_target(obj, image)
    bpy.ops.object.bake(type="EMIT", margin=16)


def create_final_material(
    base_color_image: bpy.types.Image,
    emissive_image: bpy.types.Image,
) -> bpy.types.Material:
    old_material = bpy.data.materials.get(FINAL_MATERIAL_NAME)
    if old_material:
        bpy.data.materials.remove(old_material)

    material = bpy.data.materials.new(FINAL_MATERIAL_NAME)
    material.use_nodes = True
    node_tree = material.node_tree
    principled = get_principled_node(material)

    base_node = node_tree.nodes.new(type="ShaderNodeTexImage")
    base_node.name = BASE_IMAGE_NAME
    base_node.image = base_color_image
    base_node.extension = "CLIP"

    emissive_node = node_tree.nodes.new(type="ShaderNodeTexImage")
    emissive_node.name = EMISSIVE_IMAGE_NAME
    emissive_node.image = emissive_image
    emissive_node.extension = "CLIP"

    if principled:
        node_tree.links.new(base_node.outputs["Color"], principled.inputs["Base Color"])
        if "Emission Color" in principled.inputs:
            node_tree.links.new(emissive_node.outputs["Color"], principled.inputs["Emission Color"])
        elif "Emission" in principled.inputs:
            node_tree.links.new(emissive_node.outputs["Color"], principled.inputs["Emission"])
        if "Emission Strength" in principled.inputs:
            principled.inputs["Emission Strength"].default_value = 2.2
        principled.inputs["Metallic"].default_value = 0.0
        principled.inputs["Roughness"].default_value = 0.55

    return material


def assign_final_material(obj: bpy.types.Object, material: bpy.types.Material) -> None:
    obj.data.materials.clear()
    obj.data.materials.append(material)
    for polygon in obj.data.polygons:
        polygon.material_index = 0


def main() -> None:
    if not BACKUP_PATH.exists():
        shutil.copy2(BLEND_PATH, BACKUP_PATH)

    bpy.ops.wm.open_mainfile(filepath=str(BLEND_PATH))
    mousepad = bpy.data.objects.get(MOUSEPAD_OBJECT_NAME)
    if not mousepad or mousepad.type != "MESH":
        raise RuntimeError(f"Missing mesh object: {MOUSEPAD_OBJECT_NAME}")

    mousepad.hide_set(False)
    mousepad.hide_viewport = False
    mousepad.hide_render = False

    configure_bake_settings()
    ensure_mousepad_uv(mousepad)

    base_image = create_bake_image(BASE_IMAGE_NAME)
    emissive_image = create_bake_image(EMISSIVE_IMAGE_NAME)

    bake_base_color(mousepad, base_image)
    bake_emissive(mousepad, emissive_image)

    base_image.pack()
    emissive_image.pack()

    final_material = create_final_material(base_image, emissive_image)
    assign_final_material(mousepad, final_material)

    bpy.ops.wm.save_as_mainfile(filepath=str(BLEND_PATH))
    print(f"backup={BACKUP_PATH}")
    print(f"baked={MOUSEPAD_OBJECT_NAME}")
    print(f"material={FINAL_MATERIAL_NAME}")
    print(f"images={BASE_IMAGE_NAME},{EMISSIVE_IMAGE_NAME}")


if __name__ == "__main__":
    main()
