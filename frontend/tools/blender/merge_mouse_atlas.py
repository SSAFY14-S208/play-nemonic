import argparse
import os
from pathlib import Path

import bpy


MOUSE_OBJECT_KEYWORDS = (
    "mouse_",
    "cord_mouseplug",
)
MOUSE_EMISSIVE_OBJECT_KEYWORDS = (
    "mouse_light_left",
    "mouse_light_right",
    "mouse_siderightbutton",
    "mouse_wheel_red",
)
DEFAULT_ATLAS_SIZE = 1024


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-glb", required=True)
    parser.add_argument("--output-glb", required=True)
    parser.add_argument("--atlas-size", type=int, default=DEFAULT_ATLAS_SIZE)
    if "--" in os.sys.argv:
        return parser.parse_args(os.sys.argv[os.sys.argv.index("--") + 1 :])
    return parser.parse_args()


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete()


def is_mouse_object(obj: bpy.types.Object) -> bool:
    object_name = obj.name.lower()
    return obj.type == "MESH" and any(
        keyword in object_name for keyword in MOUSE_OBJECT_KEYWORDS
    )


def is_emissive_mouse_object(obj: bpy.types.Object) -> bool:
    object_name = obj.name.lower()
    return any(keyword in object_name for keyword in MOUSE_EMISSIVE_OBJECT_KEYWORDS)


def is_emissive_mouse_part(name: str) -> bool:
    name = name.lower()
    return any(keyword in name for keyword in MOUSE_EMISSIVE_OBJECT_KEYWORDS)


def get_principled_node(material: bpy.types.Material) -> bpy.types.Node | None:
    if not material.use_nodes or not material.node_tree:
        return None
    return material.node_tree.nodes.get("Principled BSDF")


def get_source_base_color(material: bpy.types.Material) -> tuple[float, float, float, float]:
    if material.use_nodes:
        principled = get_principled_node(material)
        if principled and "Base Color" in principled.inputs:
            value = principled.inputs["Base Color"].default_value
            return (value[0], value[1], value[2], value[3])
    return tuple(material.diffuse_color)


def make_unique_material_copy(
    source_material: bpy.types.Material | None,
    object_name: str,
    slot_index: int,
) -> bpy.types.Material:
    if source_material:
        material = source_material.copy()
    else:
        material = bpy.data.materials.new("Mouse_Source_Default")
        material.diffuse_color = (0.4, 0.4, 0.4, 1.0)
        material.use_nodes = True

    material.name = f"MouseBake__{object_name}__slot_{slot_index}__{material.name}"
    return material


def uniquify_mouse_material_slots(mouse_objects: list[bpy.types.Object]) -> None:
    for obj in mouse_objects:
        for slot_index, slot in enumerate(obj.material_slots):
            slot.material = make_unique_material_copy(slot.material, obj.name, slot_index)


def create_mouse_emissive_material(
    material_name: str,
    base_color: tuple[float, float, float, float],
    emissive_strength: float,
) -> bpy.types.Material:
    material = bpy.data.materials.get(material_name)
    if material:
        return material

    material = bpy.data.materials.new(material_name)
    material.diffuse_color = base_color
    material.use_nodes = True

    principled = get_principled_node(material)
    if principled:
        principled.inputs["Base Color"].default_value = base_color
        if "Alpha" in principled.inputs:
            principled.inputs["Alpha"].default_value = base_color[3]
        if "Metallic" in principled.inputs:
            principled.inputs["Metallic"].default_value = 0.0
        if "Roughness" in principled.inputs:
            principled.inputs["Roughness"].default_value = 0.24
        if "Emission Color" in principled.inputs:
            principled.inputs["Emission Color"].default_value = base_color
        elif "Emission" in principled.inputs:
            principled.inputs["Emission"].default_value = base_color
        if "Emission Strength" in principled.inputs:
            principled.inputs["Emission Strength"].default_value = emissive_strength

    return material


def enhance_preserved_emissive_mouse_parts(mouse_objects: list[bpy.types.Object]) -> None:
    light_strip_material = create_mouse_emissive_material(
        "Mouse_LightStrip_Cyan_Emissive",
        (0.55, 1.0, 1.0, 1.0),
        2.4,
    )
    wheel_light_material = create_mouse_emissive_material(
        "Mouse_Wheel_Red_Emissive",
        (1.0, 0.08, 0.025, 1.0),
        2.8,
    )

    for obj in mouse_objects:
        object_name = obj.name.lower()
        if (
            "mouse_light_left" in object_name
            or "mouse_light_right" in object_name
            or "mouse_siderightbutton" in object_name
        ):
            obj.data.materials.clear()
            obj.data.materials.append(light_strip_material)
            for polygon in obj.data.polygons:
                polygon.material_index = 0
        elif "mouse_wheel_red" in object_name:
            obj.data.materials.clear()
            obj.data.materials.append(wheel_light_material)
            for polygon in obj.data.polygons:
                polygon.material_index = 0


def merge_mouse_objects(mouse_objects: list[bpy.types.Object]) -> bpy.types.Object:
    bpy.ops.object.select_all(action="DESELECT")
    for obj in mouse_objects:
        obj.hide_set(False)
        obj.hide_viewport = False
        obj.hide_render = False
        obj.select_set(True)

    bpy.context.view_layer.objects.active = mouse_objects[0]
    bpy.ops.object.join()
    merged_mouse = bpy.context.object
    merged_mouse.name = "Mouse_Merged_Atlas"
    merged_mouse.data.name = "Mouse_Merged_Atlas_Mesh"
    return merged_mouse


def smart_unwrap(obj: bpy.types.Object) -> None:
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj

    if not obj.data.uv_layers:
        obj.data.uv_layers.new(name="MouseAtlasUV")
    obj.data.uv_layers.active = obj.data.uv_layers[0]

    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(
        angle_limit=0.70,
        island_margin=0.035,
        area_weight=0.0,
        correct_aspect=True,
        scale_to_bounds=True,
    )
    bpy.ops.object.mode_set(mode="OBJECT")


def create_bake_image(name: str, atlas_size: int) -> bpy.types.Image:
    image = bpy.data.images.new(name, atlas_size, atlas_size, alpha=True)
    image.generated_color = (0, 0, 0, 0)
    return image


def attach_selected_image_node(
    material: bpy.types.Material,
    image: bpy.types.Image,
) -> None:
    material.use_nodes = True
    node_tree = material.node_tree
    image_node = node_tree.nodes.new(type="ShaderNodeTexImage")
    image_node.name = f"{image.name}_BakeTarget"
    image_node.image = image
    node_tree.nodes.active = image_node
    image_node.select = True


def configure_bake_settings() -> None:
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 64
    scene.cycles.use_denoising = False
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.view_settings.exposure = 0
    scene.view_settings.gamma = 1


def bake_diffuse_color(obj: bpy.types.Object, image: bpy.types.Image) -> None:
    for slot in obj.material_slots:
        if slot.material:
            attach_selected_image_node(slot.material, image)

    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.bake(type="DIFFUSE", pass_filter={"COLOR"}, margin=12)


def create_emission_bake_material(
    source_material: bpy.types.Material,
) -> bpy.types.Material:
    material = bpy.data.materials.new(f"{source_material.name}_EmitBake")
    material.use_nodes = True
    node_tree = material.node_tree
    node_tree.nodes.clear()

    output_node = node_tree.nodes.new(type="ShaderNodeOutputMaterial")
    emission_node = node_tree.nodes.new(type="ShaderNodeEmission")

    if is_emissive_mouse_part(source_material.name):
        color = get_source_base_color(source_material)
        if "mouse_wheel_red" in source_material.name.lower():
            color = (1.0, 0.05, 0.015, 1.0)
        emission_node.inputs["Color"].default_value = color
        emission_node.inputs["Strength"].default_value = 1.0
    else:
        emission_node.inputs["Color"].default_value = (0.0, 0.0, 0.0, 1.0)
        emission_node.inputs["Strength"].default_value = 0.0

    node_tree.links.new(emission_node.outputs["Emission"], output_node.inputs["Surface"])
    return material


def bake_emission(obj: bpy.types.Object, image: bpy.types.Image) -> None:
    original_materials = [slot.material for slot in obj.material_slots]
    for slot, original_material in zip(obj.material_slots, original_materials):
        if not original_material:
            continue
        emission_material = create_emission_bake_material(original_material)
        slot.material = emission_material
        attach_selected_image_node(emission_material, image)

    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.bake(type="EMIT", margin=12)

    for slot, original_material in zip(obj.material_slots, original_materials):
        slot.material = original_material


def create_final_mouse_material(
    base_color_image: bpy.types.Image,
    emissive_image: bpy.types.Image,
) -> bpy.types.Material:
    material = bpy.data.materials.new("Mouse_Atlas_Base_Emissive")
    material.use_nodes = True
    node_tree = material.node_tree
    principled = node_tree.nodes.get("Principled BSDF")

    base_color_node = node_tree.nodes.new(type="ShaderNodeTexImage")
    base_color_node.name = "Mouse_BaseColor_Atlas"
    base_color_node.image = base_color_image
    base_color_node.extension = "CLIP"

    emissive_node = node_tree.nodes.new(type="ShaderNodeTexImage")
    emissive_node.name = "Mouse_Emissive_Atlas"
    emissive_node.image = emissive_image
    emissive_node.extension = "CLIP"

    if principled:
        node_tree.links.new(base_color_node.outputs["Color"], principled.inputs["Base Color"])
        if "Emission Color" in principled.inputs:
            node_tree.links.new(
                emissive_node.outputs["Color"],
                principled.inputs["Emission Color"],
            )
        elif "Emission" in principled.inputs:
            node_tree.links.new(emissive_node.outputs["Color"], principled.inputs["Emission"])
        if "Emission Strength" in principled.inputs:
            principled.inputs["Emission Strength"].default_value = 1.65
        principled.inputs["Metallic"].default_value = 0.0
        principled.inputs["Roughness"].default_value = 0.42

    return material


def assign_single_material(obj: bpy.types.Object, material: bpy.types.Material) -> None:
    obj.data.materials.clear()
    obj.data.materials.append(material)
    for polygon in obj.data.polygons:
        polygon.material_index = 0


def export_scene(output_glb: Path) -> None:
    output_glb.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=str(output_glb),
        export_format="GLB",
        use_selection=False,
        export_apply=True,
        export_materials="EXPORT",
        export_yup=True,
        export_lights=False,
        export_cameras=False,
    )


def main() -> None:
    args = parse_args()
    input_glb = Path(args.input_glb)
    output_glb = Path(args.output_glb)

    clear_scene()
    bpy.ops.import_scene.gltf(filepath=str(input_glb))

    mouse_objects = [obj for obj in bpy.context.scene.objects if is_mouse_object(obj)]
    if not mouse_objects:
        raise RuntimeError("No mouse objects found in input GLB.")

    emissive_mouse_objects = [obj for obj in mouse_objects if is_emissive_mouse_object(obj)]
    mergeable_mouse_objects = [
        obj for obj in mouse_objects if not is_emissive_mouse_object(obj)
    ]
    if not mergeable_mouse_objects:
        raise RuntimeError("No non-emissive mouse objects found for atlas merge.")

    configure_bake_settings()
    enhance_preserved_emissive_mouse_parts(emissive_mouse_objects)
    uniquify_mouse_material_slots(mergeable_mouse_objects)
    merged_mouse = merge_mouse_objects(mergeable_mouse_objects)
    smart_unwrap(merged_mouse)

    base_color_image = create_bake_image("Mouse_BaseColor_Atlas", args.atlas_size)
    emissive_image = create_bake_image("Mouse_Emissive_Atlas", args.atlas_size)

    bake_diffuse_color(merged_mouse, base_color_image)
    bake_emission(merged_mouse, emissive_image)

    final_material = create_final_mouse_material(base_color_image, emissive_image)
    assign_single_material(merged_mouse, final_material)
    export_scene(output_glb)

    print(
        f"Mouse atlas merge complete: {len(mergeable_mouse_objects)} merged, "
        f"{len(emissive_mouse_objects)} emissive parts preserved, "
        f"output={output_glb}"
    )


if __name__ == "__main__":
    main()
