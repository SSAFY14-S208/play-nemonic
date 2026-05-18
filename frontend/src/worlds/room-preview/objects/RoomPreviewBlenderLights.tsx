import { useEffect, useMemo } from 'react'
import * as THREE from 'three'
import { RectAreaLightUniformsLib } from 'three/examples/jsm/lights/RectAreaLightUniformsLib.js'
import { ROOM_PREVIEW_SCALE } from '../constants'

type BlenderAreaLight = {
  color: [number, number, number]
  directScale: number
  energy: number
  hideRender: boolean
  name: string
  nearbyAssets: string[]
  position: [number, number, number]
  quaternion: [number, number, number, number]
  size: number
  sizeY: number
  spillScale: number
}

type BlenderPointLight = {
  color: [number, number, number]
  energy: number
  hideRender: boolean
  name: string
  position: [number, number, number]
}

const AREA_LIGHT_DIRECT_INTENSITY_SCALE = 0.14
const AREA_LIGHT_SPILL_INTENSITY_SCALE = 0.09
const AREA_LIGHT_SPILL_DISTANCE_SCALE = 1.25
const INDIRECT_LIGHT_WHITE_MIX = 0.68
const POINT_LIGHT_DISTANCE = 4.2
const POINT_LIGHT_INTENSITY_SCALE = 0.2

const BLENDER_AREA_LIGHTS: BlenderAreaLight[] = [
  {
    color: [0, 0.078737, 1],
    directScale: 0.66,
    energy: 3,
    hideRender: false,
    name: 'Blue Ambient Room light',
    nearbyAssets: [
      'WEB_SIMPLE_BACK_WALL_FOR_BAKE',
      'Desk_Table_Legs_Merged_Static',
      'Table Light strip',
    ],
    position: [0.738752, 0.756941, 0.498327],
    quaternion: [0.205468, -0.676596, 0.676596, 0.205468],
    size: 0.26,
    sizeY: 0.25,
    spillScale: 0.012,
  },
  {
    color: [0.599796, 0.067814, 1],
    directScale: 0.45,
    energy: 0.900001,
    hideRender: false,
    name: 'Corner Light',
    nearbyAssets: [
      'Shelf_Body_Merged_Static',
      'WEB_SIMPLE_LEFT_WALL_FOR_BAKE',
      'Marker case',
    ],
    position: [-0.608495, -0.144066, 0.050687],
    quaternion: [0.693663, 0.635456, -0.137228, 0.310156],
    size: 0.25,
    sizeY: 0.25,
    spillScale: 0.014,
  },
  {
    color: [0.009721, 0.144131, 0.930095],
    directScale: 0.6,
    energy: 4,
    hideRender: false,
    name: 'Cube Shelf Backlight',
    nearbyAssets: [
      'Shelf_Body_Merged_Static',
      'Desk_Table_Legs_Merged_Static',
      'Table Light strip',
    ],
    position: [-0.604867, 0.322381, 0.28495],
    quaternion: [0.683671, -0.18054, -0.683671, -0.18054],
    size: 1,
    sizeY: 1,
    spillScale: 0.016,
  },
  {
    color: [0.412539, 0.114436, 0.737924],
    directScale: 0.5,
    energy: 0.700001,
    hideRender: false,
    name: 'Cube TopShelf Bottom light',
    nearbyAssets: [
      'CommunityCanvasWhiteboard',
      'CommunityCanvasWhiteboarOutline',
      'WEB_SIMPLE_LEFT_WALL_FOR_BAKE',
    ],
    position: [-0.627877, 0.101309, 0.690429],
    quaternion: [0.707107, -0.707107, 0, 0],
    size: 1,
    sizeY: 1,
    spillScale: 0.014,
  },
  {
    color: [0.195556, 0.429227, 1],
    directScale: 0.46,
    energy: 0.4,
    hideRender: false,
    name: 'Cube TopShelf Light',
    nearbyAssets: [
      'CommunityCanvasWhiteboard',
      'CommunityCanvasWhiteboarOutline',
      'WEB_SIMPLE_LEFT_WALL_FOR_BAKE',
    ],
    position: [-0.640674, 0.110255, 0.698808],
    quaternion: [0.635092, -0.635092, -0.310899, 0.310899],
    size: 1,
    sizeY: 1,
    spillScale: 0.008,
  },
  {
    color: [0, 0.297542, 1],
    directScale: 0.62,
    energy: 0.500001,
    hideRender: false,
    name: 'Keyboard light',
    nearbyAssets: [
      'Keycaps',
      'LEDs',
      'White G915 keyboard.001',
    ],
    position: [-0.197221, 0.474772, 0.330733],
    quaternion: [0.679873, 0.73333, 0, 0],
    size: 0.87782,
    sizeY: 1,
    spillScale: 0.012,
  },
  {
    color: [0.008023, 0.132871, 0.896255],
    directScale: 0.58,
    energy: 1.200001,
    hideRender: false,
    name: 'Monitor Backlight',
    nearbyAssets: [
      'Monitor Base',
      'Monitor Back',
      'Screen',
    ],
    position: [-0.243077, 0.692823, 0.49685],
    quaternion: [0.999999, 0, 0.001123, 0],
    size: 1,
    sizeY: 1,
    spillScale: 0.012,
  },
  {
    color: [1, 0.072638, 0.004611],
    directScale: 0.52,
    energy: 2,
    hideRender: false,
    name: 'Orange Ambient Room light',
    nearbyAssets: [
      'WEB_SIMPLE_FLOOR_FOR_BAKE',
      'Desk_Table_Legs_Merged_Static',
      'Carpet',
    ],
    position: [0.196987, -0.587465, 0.29693],
    quaternion: [1, 0, 0, 0],
    size: 0.25,
    sizeY: 0.25,
    spillScale: 0.012,
  },
  {
    color: [0.371303, 0, 1],
    directScale: 0.68,
    energy: 7,
    hideRender: false,
    name: 'Table Backlight',
    nearbyAssets: [
      'Desk_Table_Legs_Merged_Static',
      'Table Light strip',
      'WEB_SIMPLE_BACK_WALL_FOR_BAKE',
    ],
    position: [0.085967, 0.747334, 0.307049],
    quaternion: [0, 0, 0.999911, -0.013345],
    size: 1,
    sizeY: 1,
    spillScale: 0.022,
  },
  {
    color: [1, 0.142485, 0.019226],
    directScale: 0,
    energy: 2,
    hideRender: true,
    name: 'Top Corner Room light',
    nearbyAssets: [
      'WEB_SIMPLE_LEFT_WALL_FOR_BAKE',
      'Acoustic wood_block.001',
    ],
    position: [-0.59301, 0.195881, 1.374125],
    quaternion: [0.5, -0.5, 0.5, 0.5],
    size: 0.25,
    sizeY: 0.25,
    spillScale: 0,
  },
  {
    color: [1, 0.155862, 0.044542],
    directScale: 1.8,
    energy: 2,
    hideRender: false,
    name: 'Top Room Light',
    nearbyAssets: [
      'WEB_SIMPLE_BACK_WALL_FOR_BAKE',
      'Acoustic wood_block.001',
      'WEB_SIMPLE_LEFT_WALL_FOR_BAKE',
    ],
    position: [-0.009952, 0.759823, 1.374125],
    quaternion: [0.707107, -0.707107, 0, 0],
    size: 0.25,
    sizeY: 0.25,
    spillScale: 0.026,
  },
]

const BLENDER_POINT_LIGHTS: BlenderPointLight[] = [
  {
    color: [0.388454, 0.420836, 1],
    energy: 0.200001,
    hideRender: false,
    name: 'Side Table Ambient',
    position: [0.488828, 0.043036, 0.421928],
  },
  {
    color: [0.24571, 0.341081, 1],
    energy: 0.200001,
    hideRender: false,
    name: 'Side Table Ambient.001',
    position: [0.663977, -0.107391, 0.421928],
  },
  {
    color: [0.388454, 0.420836, 1],
    energy: 3,
    hideRender: false,
    name: 'Side Table Ambient.002',
    position: [0.488828, 0.359025, 0.421928],
  },
]

function toGltfPosition([
  blenderX,
  blenderY,
  blenderZ,
]: [number, number, number]): [number, number, number] {
  return [blenderX, blenderZ, -blenderY]
}

function BlenderAreaLightMesh({ light }: { light: BlenderAreaLight }) {
  const color = useMemo(() => new THREE.Color(...light.color), [light.color])
  const indirectColor = useMemo(
    () =>
      new THREE.Color(...light.color).lerp(
        new THREE.Color(1, 1, 1),
        INDIRECT_LIGHT_WHITE_MIX,
      ),
    [light.color],
  )
  const position = useMemo(() => toGltfPosition(light.position), [light.position])
  const quaternion = useMemo(() => new THREE.Quaternion(...light.quaternion), [
    light.quaternion,
  ])
  const width = light.size * ROOM_PREVIEW_SCALE
  const height = light.sizeY * ROOM_PREVIEW_SCALE
  const spillDistance =
    Math.max(width, height) * AREA_LIGHT_SPILL_DISTANCE_SCALE
  const spillIntensity =
    light.energy * light.spillScale * AREA_LIGHT_SPILL_INTENSITY_SCALE

  if (light.hideRender) return null

  return (
    <>
      <rectAreaLight
        color={color}
        height={height}
        intensity={
          light.energy * light.directScale * AREA_LIGHT_DIRECT_INTENSITY_SCALE
        }
        name={light.name}
        position={position}
        quaternion={quaternion}
        userData={{ nearbyAssets: light.nearbyAssets }}
        width={width}
      />
      {spillIntensity > 0 && (
        <pointLight
          color={indirectColor}
          decay={2}
          distance={spillDistance}
          intensity={spillIntensity}
          name={`${light.name} Cycles spill`}
          position={position}
        />
      )}
    </>
  )
}

function BlenderPointLightMesh({ light }: { light: BlenderPointLight }) {
  const color = useMemo(() => new THREE.Color(...light.color), [light.color])
  const position = useMemo(() => toGltfPosition(light.position), [light.position])

  if (light.hideRender) return null

  return (
    <pointLight
      color={color}
      decay={2}
      distance={POINT_LIGHT_DISTANCE}
      intensity={light.energy * POINT_LIGHT_INTENSITY_SCALE}
      name={light.name}
      position={position}
    />
  )
}

export default function RoomPreviewBlenderLights() {
  useEffect(() => {
    RectAreaLightUniformsLib.init()
  }, [])

  return (
    <>
      {BLENDER_AREA_LIGHTS.map((light) => (
        <BlenderAreaLightMesh
          key={light.name}
          light={light}
        />
      ))}
      {BLENDER_POINT_LIGHTS.map((light) => (
        <BlenderPointLightMesh
          key={light.name}
          light={light}
        />
      ))}
    </>
  )
}
