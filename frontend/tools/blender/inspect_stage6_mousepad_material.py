from __future__ import annotations

from pathlib import Path

import bpy


BLEND_PATH = Path(
    r"C:\Users\SSAFY\Desktop\망고슬래브\에셋\3D에셋\pink room\Room_web_bake_stage6_no_glass_static.blend"
)


def printable_value(value):
    try:
        return tuple(round(item, 3) for item in value)
    except TypeError:
        if isinstance(value, float):
            return round(value, 3)
        return value


def main() -> None:
    bpy.ops.wm.open_mainfile(filepath=str(BLEND_PATH))
    obj = bpy.data.objects.get("MousePAd")
    print(f"OBJ={bool(obj)}")
    if not obj:
        return

    print(f"DIMS={tuple(round(value, 4) for value in obj.dimensions)}")
    print(f"POLYGON_MATERIAL_INDICES={sorted(set(poly.material_index for poly in obj.data.polygons))}")

    interesting_inputs = {
        "Alpha",
        "Base Color",
        "Color",
        "Emission",
        "Emission Color",
        "Emission Strength",
        "Metallic",
        "Roughness",
        "Strength",
    }
    for slot in obj.material_slots:
        material = slot.material
        print(f"MAT={material.name if material else None}")
        if not material:
            continue
        print(f"  diffuse={printable_value(material.diffuse_color)} use_nodes={material.use_nodes}")
        if not material.use_nodes or not material.node_tree:
            continue
        for node in material.node_tree.nodes:
            print(f"  NODE={node.bl_idname}|{node.name}")
            if node.bl_idname == "ShaderNodeTexImage" and node.image:
                print(f"    image={node.image.name} size={node.image.size[:]} packed={bool(node.image.packed_file)}")
            for input_socket in node.inputs:
                if input_socket.name not in interesting_inputs:
                    continue
                if input_socket.is_linked:
                    print(f"    input={input_socket.name} linked")
                elif hasattr(input_socket, "default_value"):
                    print(f"    input={input_socket.name} value={printable_value(input_socket.default_value)}")


if __name__ == "__main__":
    main()
