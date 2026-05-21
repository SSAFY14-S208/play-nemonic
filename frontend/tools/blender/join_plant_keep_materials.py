import argparse
import os
from pathlib import Path

import bpy


PLANT_OBJECT_NAMES = {
    "succulent plant",
}
PLANT_MATERIAL_NAMES = {
    "succulent material",
    "porcelain",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-glb", required=True)
    parser.add_argument("--output-glb", required=True)
    if "--" in os.sys.argv:
        return parser.parse_args(os.sys.argv[os.sys.argv.index("--") + 1 :])
    return parser.parse_args()


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete()


def is_plant_mesh(obj: bpy.types.Object) -> bool:
    if obj.type != "MESH":
        return False

    object_name = obj.name.lower()
    material_names = {
        slot.material.name.lower()
        for slot in obj.material_slots
        if slot.material
    }

    return (
        any(
            object_name == plant_name or object_name.startswith(f"{plant_name}.")
            for plant_name in PLANT_OBJECT_NAMES
        )
        or bool(material_names.intersection(PLANT_MATERIAL_NAMES))
    )


def join_plant_meshes(plant_meshes: list[bpy.types.Object]) -> bpy.types.Object:
    bpy.ops.object.select_all(action="DESELECT")
    for obj in plant_meshes:
        obj.hide_set(False)
        obj.hide_viewport = False
        obj.hide_render = False
        obj.select_set(True)

    bpy.context.view_layer.objects.active = plant_meshes[0]
    bpy.ops.object.join()
    joined_plant = bpy.context.object
    joined_plant.name = "Plant_Joined_SourceMaterials"
    joined_plant.data.name = "Plant_Joined_SourceMaterials_Mesh"
    return joined_plant


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

    plant_meshes = [obj for obj in bpy.context.scene.objects if is_plant_mesh(obj)]
    if len(plant_meshes) < 2:
        raise RuntimeError(f"Expected at least 2 plant meshes, found {len(plant_meshes)}.")

    joined_plant = join_plant_meshes(plant_meshes)
    export_scene(output_glb)

    material_names = [
        slot.material.name
        for slot in joined_plant.material_slots
        if slot.material
    ]
    print(
        f"Plant source-material join complete: {len(plant_meshes)} objects -> "
        f"{joined_plant.name}, materials={material_names}, output={output_glb}"
    )


if __name__ == "__main__":
    main()
