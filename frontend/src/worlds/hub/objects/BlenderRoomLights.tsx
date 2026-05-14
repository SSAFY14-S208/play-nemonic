import { useEffect, useMemo } from 'react'
import { RectAreaLightUniformsLib } from 'three/examples/jsm/lights/RectAreaLightUniformsLib.js'
import * as THREE from 'three'
import { HUB_ROOM_POSITION, HUB_ROOM_SCALE } from '@/shared/constants'

type BlenderAreaLight = {
  name: string
  position: [number, number, number]
  rotation: [number, number, number]
  color: string
  energy: number
  intensityScale: number
  spillScale: number
  size: number
  sizeY: number
}

type BlenderPointLight = {
  name: string
  position: [number, number, number]
  color: string
  energy: number
}

const BLENDER_AREA_LIGHTS: BlenderAreaLight[] = [
  {
    name: 'Table Backlight',
    position: [0.086, 0.7473, 0.307],
    rotation: [7.8273, 0, 0],
    color: '#5f00ff',
    energy: 5,
    intensityScale: 0.95,
    spillScale: 0.07,
    size: 1,
    sizeY: 1,
  },
  {
    name: 'Keyboard light',
    position: [-0.1972, 0.4748, 0.3307],
    rotation: [3.2172, 0, 0],
    color: '#004cff',
    energy: 0.5,
    intensityScale: 0.72,
    spillScale: 0.03,
    size: 0.878,
    sizeY: 1,
  },
  {
    name: 'Corner Light',
    position: [-0.6085, -0.1441, 0.0507],
    rotation: [3.1416, -0.2588, -0.6494],
    color: '#9911ff',
    energy: 0.9,
    intensityScale: 0.74,
    spillScale: 0.07,
    size: 0.25,
    sizeY: 0.25,
  },
  {
    name: 'Cube Shelf Backlight',
    position: [-0.6049, 0.3224, 0.285],
    rotation: [8.3703, 0, 1.5708],
    color: '#0225ed',
    energy: 4,
    intensityScale: 0.38,
    spillScale: 0.026,
    size: 1,
    sizeY: 1,
  },
  {
    name: 'Top Room Light',
    position: [-0.01, 0.7598, 1.0465],
    rotation: [0, 0, 0],
    color: '#ff280b',
    energy: 1,
    intensityScale: 0.72,
    spillScale: 0.05,
    size: 0.25,
    sizeY: 0.25,
  },
  {
    name: 'Top Corner Room light',
    position: [-0.593, 0.1959, 1.0465],
    rotation: [0, 0, 1.5708],
    color: '#ff2405',
    energy: 1,
    intensityScale: 0.66,
    spillScale: 0.045,
    size: 0.25,
    sizeY: 0.25,
  },
  {
    name: 'Blue Ambient Room light',
    position: [0.7388, 0.7569, 0.4983],
    rotation: [0, 0.9811, 1.5708],
    color: '#0014ff',
    energy: 3,
    intensityScale: 0.12,
    spillScale: 0.012,
    size: 0.26,
    sizeY: 0.25,
  },
  {
    name: 'Orange Ambient Room light',
    position: [0.197, -0.5875, 0.2969],
    rotation: [1.5708, 0, 0],
    color: '#ff1301',
    energy: 2,
    intensityScale: 1.05,
    spillScale: 0.08,
    size: 0.25,
    sizeY: 0.25,
  },
  {
    name: 'Cube TopShelf Bottom light',
    position: [-0.6333, 0.0761, 0.622],
    rotation: [0, 0, 0],
    color: '#691dba',
    energy: 0.7,
    intensityScale: 0.52,
    spillScale: 0.04,
    size: 1,
    sizeY: 1,
  },
  {
    name: 'Cube TopShelf Light',
    position: [-0.6405, 0.0801, 0.736],
    rotation: [0, -0.9105, 0],
    color: '#326dff',
    energy: 0.4,
    intensityScale: 0.34,
    spillScale: 0.02,
    size: 1,
    sizeY: 1,
  },
  {
    name: 'Frame light',
    position: [-0.616, 0.0653, 0.9602],
    rotation: [0, 0, 0],
    color: '#691dba',
    energy: 0.7,
    intensityScale: 0.52,
    spillScale: 0.04,
    size: 1,
    sizeY: 1,
  },
  {
    name: 'Monitor Backlight',
    position: [-0.2431, 0.6928, 0.4969],
    rotation: [1.5708, 0, 0.0022],
    color: '#0222e5',
    energy: 1.2,
    intensityScale: 0.74,
    spillScale: 0.045,
    size: 1,
    sizeY: 1,
  },
]

const BLENDER_POINT_LIGHTS: BlenderPointLight[] = [
  {
    name: 'Side Table Ambient.002',
    position: [0.4888, 0.359, 0.4219],
    color: '#636bff',
    energy: 0.2,
  },
  {
    name: 'Side Table Ambient',
    position: [0.4888, 0.043, 0.4219],
    color: '#636bff',
    energy: 0.2,
  },
  {
    name: 'Side Table Ambient.001',
    position: [0.664, -0.1074, 0.4219],
    color: '#3f57ff',
    energy: 0.2,
  },
]

const blenderToThreeConversion = new THREE.Matrix4().makeRotationX(-Math.PI / 2)
const inverseBlenderToThreeConversion = blenderToThreeConversion.clone().invert()

function toThreePosition([x, y, z]: [number, number, number]): [number, number, number] {
  return [
    HUB_ROOM_POSITION[0] + x * HUB_ROOM_SCALE,
    HUB_ROOM_POSITION[1] + z * HUB_ROOM_SCALE,
    HUB_ROOM_POSITION[2] - y * HUB_ROOM_SCALE,
  ]
}

function toThreeQuaternion(rotation: [number, number, number]) {
  const blenderRotation = new THREE.Matrix4().makeRotationFromEuler(
    new THREE.Euler(rotation[0], rotation[1], rotation[2], 'XYZ'),
  )
  const threeRotation = blenderToThreeConversion
    .clone()
    .multiply(blenderRotation)
    .multiply(inverseBlenderToThreeConversion)

  return new THREE.Quaternion().setFromRotationMatrix(threeRotation)
}

function BlenderAreaLightMesh({ light }: { light: BlenderAreaLight }) {
  const position = useMemo(() => toThreePosition(light.position), [light.position])
  const quaternion = useMemo(() => toThreeQuaternion(light.rotation), [light.rotation])
  const width = light.size * HUB_ROOM_SCALE
  const height = light.sizeY * HUB_ROOM_SCALE

  return (
    <>
      <rectAreaLight
        color={light.color}
        height={height}
        intensity={light.energy * light.intensityScale}
        position={position}
        quaternion={quaternion}
        width={width}
      />
      <pointLight
        color={light.color}
        distance={Math.max(width, height) * 1.8}
        intensity={light.energy * light.spillScale}
        position={position}
      />
    </>
  )
}

function BlenderPointLightMesh({ light }: { light: BlenderPointLight }) {
  const position = useMemo(() => toThreePosition(light.position), [light.position])

  return (
    <pointLight
      color={light.color}
      distance={3.6}
      intensity={light.energy * 2.1}
      position={position}
    />
  )
}

export default function BlenderRoomLights() {
  useEffect(() => {
    RectAreaLightUniformsLib.init()
  }, [])

  return (
    <>
      {BLENDER_AREA_LIGHTS.map((light) => (
        <BlenderAreaLightMesh key={light.name} light={light} />
      ))}
      {BLENDER_POINT_LIGHTS.map((light) => (
        <BlenderPointLightMesh key={light.name} light={light} />
      ))}
    </>
  )
}
