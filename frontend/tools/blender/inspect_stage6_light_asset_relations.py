import json
import math

import bpy
from mathutils import Matrix, Vector


def round_vector(values):
    return [round(float(value), 6) for value in values]


def to_gltf_tuple(values):
    return [
        round(float(values[0]), 6),
        round(float(values[2]), 6),
        round(float(-values[1]), 6),
    ]


def to_gltf_vector(value):
    return Vector((float(value.x), float(value.z), float(-value.y)))


def get_gltf_quaternion_from_blender_axes(quaternion):
    x_axis = to_gltf_vector(quaternion @ Vector((1, 0, 0))).normalized()
    y_axis = to_gltf_vector(quaternion @ Vector((0, 1, 0))).normalized()
    z_axis = to_gltf_vector(quaternion @ Vector((0, 0, 1))).normalized()
    gltf_matrix = Matrix(
        (
            (x_axis.x, y_axis.x, z_axis.x),
            (x_axis.y, y_axis.y, z_axis.y),
            (x_axis.z, y_axis.z, z_axis.z),
        ),
    )

    return gltf_matrix.to_quaternion()


def vector_from_tuple(values):
    return Vector((values[0], values[1], values[2]))


def object_bbox_world(obj):
    try:
        corners = []
        for corner in obj.bound_box:
            local_corner = Vector(
                (float(corner[0]), float(corner[1]), float(corner[2])),
            )
            world_corner = obj.matrix_world @ local_corner
            corners.append(
                (float(world_corner.x), float(world_corner.y), float(world_corner.z)),
            )
    except Exception:
        return None

    x_values = [corner[0] for corner in corners]
    y_values = [corner[1] for corner in corners]
    z_values = [corner[2] for corner in corners]
    min_value = (min(x_values), min(y_values), min(z_values))
    max_value = (max(x_values), max(y_values), max(z_values))
    center = (
        (min_value[0] + max_value[0]) * 0.5,
        (min_value[1] + max_value[1]) * 0.5,
        (min_value[2] + max_value[2]) * 0.5,
    )
    size = (
        max_value[0] - min_value[0],
        max_value[1] - min_value[1],
        max_value[2] - min_value[2],
    )

    return {
        "center": center,
        "max": max_value,
        "min": min_value,
        "size": size,
    }


def distance_to_bbox(point, bbox):
    point_x, point_y, point_z = point
    min_x, min_y, min_z = bbox["min"]
    max_x, max_y, max_z = bbox["max"]
    delta_x = max(min_x - point_x, 0, point_x - max_x)
    delta_y = max(min_y - point_y, 0, point_y - max_y)
    delta_z = max(min_z - point_z, 0, point_z - max_z)

    return math.sqrt(delta_x * delta_x + delta_y * delta_y + delta_z * delta_z)


def get_mesh_infos():
    mesh_infos = []
    for obj in bpy.context.scene.objects:
        if obj.type != "MESH":
            continue
        if obj.hide_get() or obj.hide_render:
            continue

        bbox = object_bbox_world(obj)
        if bbox is None:
            continue

        mesh_infos.append(
            {
                "bbox": bbox,
                "center": bbox["center"],
                "name": obj.name,
                "size": bbox["size"],
            },
        )

    return mesh_infos


def get_nearest_meshes(light_location, light_direction, mesh_infos):
    nearest_meshes = []
    light_location_vector = vector_from_tuple(light_location)
    light_direction_vector = vector_from_tuple(light_direction)

    for mesh_info in mesh_infos:
        center_vector = vector_from_tuple(mesh_info["center"])
        center_offset = center_vector - light_location_vector
        facing_dot = None

        if center_offset.length > 0:
            facing_dot = light_direction_vector.normalized().dot(
                center_offset.normalized(),
            )

        nearest_meshes.append(
            {
                "center": round_vector(mesh_info["center"]),
                "centerDistance": round(float(center_offset.length), 6),
                "distance": round(
                    float(distance_to_bbox(light_location, mesh_info["bbox"])),
                    6,
                ),
                "facingDot": None if facing_dot is None else round(float(facing_dot), 6),
                "gltfCenter": to_gltf_tuple(mesh_info["center"]),
                "name": mesh_info["name"],
                "size": round_vector(mesh_info["size"]),
            },
        )

    nearest_meshes.sort(
        key=lambda item: (
            item["distance"],
            -999 if item["facingDot"] is None else -item["facingDot"],
        ),
    )

    return nearest_meshes[:10]


def inspect_lights():
    mesh_infos = get_mesh_infos()
    lights = []

    for obj in bpy.context.scene.objects:
        if obj.type != "LIGHT":
            continue

        light_data = obj.data
        location_vector = obj.matrix_world.translation
        location = (
            float(location_vector.x),
            float(location_vector.y),
            float(location_vector.z),
        )
        quaternion = obj.matrix_world.to_quaternion()
        direction_vector = quaternion @ Vector((0, 0, -1))
        direction = (
            float(direction_vector.x),
            float(direction_vector.y),
            float(direction_vector.z),
        )

        light_info = {
            "color": round_vector(light_data.color),
            "directionMinusZ": round_vector(direction),
            "energy": round(float(light_data.energy), 6),
            "gltfDirectionMinusZ": to_gltf_tuple(direction),
            "gltfLocation": to_gltf_tuple(location),
            "gltfQuaternion": round_vector(
                get_gltf_quaternion_from_blender_axes(quaternion),
            ),
            "hideRender": bool(obj.hide_render),
            "location": round_vector(location),
            "name": obj.name,
            "nearestMeshes": get_nearest_meshes(location, direction, mesh_infos),
            "quaternion": round_vector(quaternion),
            "rotationEuler": round_vector(obj.rotation_euler),
            "shadowSoftSize": round(float(getattr(light_data, "shadow_soft_size", 0)), 6),
            "type": light_data.type,
            "visible": bool(obj.visible_get()),
        }

        if light_data.type == "AREA":
            light_info.update(
                {
                    "shape": light_data.shape,
                    "size": round(float(light_data.size), 6),
                    "sizeY": round(float(light_data.size_y), 6),
                    "spread": round(float(getattr(light_data, "spread", 0)), 6),
                },
            )

        if light_data.type in {"POINT", "SPOT"}:
            light_info.update(
                {
                    "cutoffDistance": round(
                        float(getattr(light_data, "cutoff_distance", 0)),
                        6,
                    ),
                    "useCustomDistance": bool(
                        getattr(light_data, "use_custom_distance", False),
                    ),
                },
            )

        lights.append(light_info)

    return lights


def main():
    world = bpy.context.scene.world
    result = {
        "blend": bpy.data.filepath,
        "lights": inspect_lights(),
        "renderEngine": bpy.context.scene.render.engine,
        "viewSettings": {
            "exposure": bpy.context.scene.view_settings.exposure,
            "gamma": bpy.context.scene.view_settings.gamma,
            "look": bpy.context.scene.view_settings.look,
            "view_transform": bpy.context.scene.view_settings.view_transform,
        },
        "world": None if world is None else {"color": round_vector(world.color)},
    }

    print("STAGE6_LIGHT_AUDIT_JSON_START")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print("STAGE6_LIGHT_AUDIT_JSON_END")


if __name__ == "__main__":
    main()
