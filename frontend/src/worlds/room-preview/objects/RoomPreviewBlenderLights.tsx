import { useEffect, useMemo, useRef } from 'react'
import { useFrame, useThree } from '@react-three/fiber'
import * as THREE from 'three'
import { RectAreaLightUniformsLib } from 'three/examples/jsm/lights/RectAreaLightUniformsLib.js'
import { HUB_GAMES } from '@/shared/constants'
import { useHubGameStore } from '@/shared/stores'
import { ROOM_PREVIEW_SCALE } from '../constants'
import {
  type RoomPreviewLightDebugVector3,
  useRoomPreviewLightDebugStore,
} from '../light-debug/roomPreviewLightDebugStore'

export type RoomPreviewBlenderAreaLight = {
  color: [number, number, number]
  defaultDirectMultiplier?: number
  defaultHeightMultiplier?: number
  defaultSpillMultiplier?: number
  defaultWidthMultiplier?: number
  directScale: number
  energy: number
  hideRender: boolean
  name: string
  nearbyAssets: string[]
  position: [number, number, number]
  positionOffset?: RoomPreviewLightDebugVector3
  quaternion: [number, number, number, number]
  rotationOffset?: RoomPreviewLightDebugVector3
  size: number
  sizeY: number
  spillScale: number
}

export type RoomPreviewBlenderPointLight = {
  color: [number, number, number]
  defaultPointMultiplier?: number
  defaultPointRangeMultiplier?: number
  energy: number
  hideRender: boolean
  name: string
  position: [number, number, number]
  positionOffset?: RoomPreviewLightDebugVector3
}

const AREA_LIGHT_DIRECT_INTENSITY_SCALE = 0.14
const AREA_LIGHT_SPILL_INTENSITY_SCALE = 0.09
const AREA_LIGHT_SPILL_DISTANCE_SCALE = 1.25
const INDIRECT_LIGHT_WHITE_MIX = 0.68
const POINT_LIGHT_DISTANCE = 4.2
const POINT_LIGHT_INTENSITY_SCALE = 0.2
const ZERO_VECTOR3: RoomPreviewLightDebugVector3 = [0, 0, 0]
const GAME_LIGHT_COLOR_TRANSITION_SPEED = 4.8
const GAME_LIGHT_COLOR_SETTLE_DISTANCE = 0.0025
const DEFAULT_HUB_GAME_LIGHTING_COLOR = '#a281d0'

const GAME_LIGHT_DIRECT_TINT_STRENGTH: Record<string, number> = {
  'Monitor Backlight': 1,
  'Table Backlight': 0.78,
  'Cube TopShelf Bottom light': 0.58,
  'Top Room Light': 0.24,
}

const GAME_LIGHT_SPILL_TINT_STRENGTH: Record<string, number> = {
  'Monitor Backlight': 0.92,
  'Table Backlight': 0.6,
  'Cube TopShelf Bottom light': 0.4,
  'Top Room Light': 0.18,
}

const GAME_POINT_LIGHT_TINT_STRENGTH = 0.42

export const ROOM_PREVIEW_BLENDER_AREA_LIGHTS: RoomPreviewBlenderAreaLight[] = [
  {
    color: [0.412539, 0.114436, 0.737924],
    defaultDirectMultiplier: 6,
    defaultHeightMultiplier: 3.95,
    defaultSpillMultiplier: 6,
    defaultWidthMultiplier: 3.72,
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
    positionOffset: [1.5, 0.79, 1.07],
    quaternion: [0.707107, -0.707107, 0, 0],
    rotationOffset: [86, -44, -122],
    size: 1,
    sizeY: 1,
    spillScale: 0.014,
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
    defaultDirectMultiplier: 4.92,
    defaultHeightMultiplier: 0.42,
    defaultSpillMultiplier: 6,
    defaultWidthMultiplier: 0.59,
    position: [-0.243077, 0.692823, 0.49685],
    positionOffset: [0, 0.05, 0],
    quaternion: [0.999999, 0, 0.001123, 0],
    rotationOffset: [-167, 0, 0],
    size: 1,
    sizeY: 1,
    spillScale: 0.012,
  },
  {
    color: [0.371303, 0, 1],
    defaultDirectMultiplier: 1.1,
    defaultHeightMultiplier: 0.37,
    defaultSpillMultiplier: 6,
    defaultWidthMultiplier: 1.27,
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
    positionOffset: [0, -0.05, 0],
    quaternion: [0, 0, 0.999911, -0.013345],
    rotationOffset: [-65, 0, 0],
    size: 1,
    sizeY: 1,
    spillScale: 0.022,
  },
  {
    color: [1, 0.155862, 0.044542],
    defaultDirectMultiplier: 2.18,
    defaultHeightMultiplier: 1.63,
    defaultSpillMultiplier: 6,
    defaultWidthMultiplier: 4,
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
    positionOffset: [-0.34, -0.55, 0.11],
    quaternion: [0.707107, -0.707107, 0, 0],
    rotationOffset: [-8, -89, -30],
    size: 0.25,
    sizeY: 0.25,
    spillScale: 0.026,
  },
]

export const ROOM_PREVIEW_BLENDER_POINT_LIGHTS: RoomPreviewBlenderPointLight[] = [
  {
    color: [0.388454, 0.420836, 1],
    defaultPointMultiplier: 2.29,
    defaultPointRangeMultiplier: 0.43,
    energy: 3,
    hideRender: false,
    name: 'Side Table Ambient.002',
    position: [0.488828, 0.359025, 0.421928],
    positionOffset: [-0.95, -0.24, 0.15],
  },
]

function toGltfPosition([
  blenderX,
  blenderY,
  blenderZ,
]: [number, number, number]): [number, number, number] {
  return [blenderX, blenderZ, -blenderY]
}

function addVector3(
  vector: [number, number, number],
  offset: RoomPreviewLightDebugVector3,
): [number, number, number] {
  return [
    vector[0] + offset[0],
    vector[1] + offset[1],
    vector[2] + offset[2],
  ]
}

function clampHelperPlaneSize(value: number) {
  return Math.min(Math.max(value, 0.08), 0.9)
}

function getHubGameLightingColor(selectedGameIndex: number) {
  const selectedGame = HUB_GAMES[selectedGameIndex] ?? HUB_GAMES[0]

  return selectedGame?.lightingColor ?? DEFAULT_HUB_GAME_LIGHTING_COLOR
}

function getGameTintedLightColor(
  baseColor: THREE.Color,
  gameLightingColor: string | null,
  tintStrength: number,
) {
  if (!gameLightingColor || tintStrength <= 0) return baseColor.clone()

  return baseColor.clone().lerp(new THREE.Color(gameLightingColor), tintStrength)
}

function getColorDistance(firstColor: THREE.Color, secondColor: THREE.Color) {
  const redDistance = firstColor.r - secondColor.r
  const greenDistance = firstColor.g - secondColor.g
  const blueDistance = firstColor.b - secondColor.b

  return Math.sqrt(
    redDistance * redDistance +
      greenDistance * greenDistance +
      blueDistance * blueDistance,
  )
}

function advanceAnimatedLightColor(
  currentColor: THREE.Color,
  targetColor: THREE.Color,
  delta: number,
) {
  if (
    getColorDistance(currentColor, targetColor) <=
    GAME_LIGHT_COLOR_SETTLE_DISTANCE
  ) {
    currentColor.copy(targetColor)
    return false
  }

  const transitionProgress =
    1 - Math.exp(-GAME_LIGHT_COLOR_TRANSITION_SPEED * delta)
  currentColor.lerp(targetColor, transitionProgress)

  return (
    getColorDistance(currentColor, targetColor) >
    GAME_LIGHT_COLOR_SETTLE_DISTANCE
  )
}

function useLightDebugRuntime(
  lightName: string,
  defaultEnabled: boolean,
  defaultPositionOffset: RoomPreviewLightDebugVector3 = ZERO_VECTOR3,
  defaultRotationOffset: RoomPreviewLightDebugVector3 = ZERO_VECTOR3,
  defaultDirectMultiplier = 1,
  defaultSpillMultiplier = 1,
  defaultPointMultiplier = 1,
  defaultWidthMultiplier = 1,
  defaultHeightMultiplier = 1,
  defaultPointRangeMultiplier = 1,
) {
  const areaDirectMaster = useRoomPreviewLightDebugStore(
    (state) => state.areaDirectMaster,
  )
  const areaSpillMaster = useRoomPreviewLightDebugStore(
    (state) => state.areaSpillMaster,
  )
  const isDebugEnabled = useRoomPreviewLightDebugStore(
    (state) => state.isDebugEnabled,
  )
  const lightOverride = useRoomPreviewLightDebugStore(
    (state) => state.lightOverrides[lightName],
  )
  const pointMaster = useRoomPreviewLightDebugStore((state) => state.pointMaster)
  const selectedLightName = useRoomPreviewLightDebugStore(
    (state) => state.selectedLightName,
  )
  const showHelpers = useRoomPreviewLightDebugStore(
    (state) => state.showHelpers,
  )
  const soloLightName = useRoomPreviewLightDebugStore(
    (state) => state.soloLightName,
  )

  if (!isDebugEnabled) {
    return {
      areaDirectMultiplier: defaultDirectMultiplier,
      areaSpillMultiplier: defaultSpillMultiplier,
      heightMultiplier: defaultHeightMultiplier,
      isDebugEnabled,
      isLightEnabled: defaultEnabled,
      isLightVisible: defaultEnabled,
      isSelected: false,
      positionOffset: defaultPositionOffset,
      pointMultiplier: defaultPointMultiplier,
      pointRangeMultiplier: defaultPointRangeMultiplier,
      rotationOffset: defaultRotationOffset,
      showHelpers: false,
      widthMultiplier: defaultWidthMultiplier,
    }
  }

  const isLightEnabled = lightOverride?.enabled ?? defaultEnabled
  const isSoloAllowed = !soloLightName || soloLightName === lightName

  return {
    areaDirectMultiplier:
      areaDirectMaster *
      (lightOverride?.directMultiplier ?? defaultDirectMultiplier),
    areaSpillMultiplier:
      areaSpillMaster *
      (lightOverride?.spillMultiplier ?? defaultSpillMultiplier),
    heightMultiplier:
      lightOverride?.heightMultiplier ?? defaultHeightMultiplier,
    isDebugEnabled,
    isLightEnabled,
    isLightVisible: isLightEnabled && isSoloAllowed,
    isSelected: selectedLightName === lightName,
    positionOffset: lightOverride?.positionOffset ?? defaultPositionOffset,
    pointMultiplier:
      pointMaster * (lightOverride?.pointMultiplier ?? defaultPointMultiplier),
    pointRangeMultiplier:
      lightOverride?.pointRangeMultiplier ?? defaultPointRangeMultiplier,
    rotationOffset: lightOverride?.rotationOffset ?? defaultRotationOffset,
    showHelpers,
    widthMultiplier: lightOverride?.widthMultiplier ?? defaultWidthMultiplier,
  }
}

function BlenderLightDebugHelper({
  color,
  isLightEnabled,
  isSelected,
  planeSize,
  position,
  quaternion,
}: {
  color: [number, number, number]
  isLightEnabled: boolean
  isSelected: boolean
  planeSize?: [number, number]
  position: [number, number, number]
  quaternion?: THREE.Quaternion
}) {
  const helperColor = useMemo(() => new THREE.Color(...color), [color])

  return (
    <group position={position}>
      <mesh
        renderOrder={50}
        scale={isSelected ? 1.35 : 1}
      >
        <sphereGeometry args={[0.035, 12, 12]} />
        <meshBasicMaterial
          color={helperColor}
          depthTest={false}
          depthWrite={false}
          opacity={isLightEnabled ? 0.95 : 0.25}
          toneMapped={false}
          transparent
        />
      </mesh>
      {quaternion && (
        <mesh
          quaternion={quaternion}
          renderOrder={49}
          scale={isSelected ? 1.35 : 1}
        >
          <planeGeometry args={planeSize ?? [0.18, 0.18]} />
          <meshBasicMaterial
            color={helperColor}
            depthTest={false}
            depthWrite={false}
            opacity={isLightEnabled ? 0.45 : 0.16}
            toneMapped={false}
            transparent
            wireframe
          />
        </mesh>
      )}
    </group>
  )
}

function BlenderAreaLightMesh({
  gameLightingColor,
  light,
}: {
  gameLightingColor: string | null
  light: RoomPreviewBlenderAreaLight
}) {
  const directLightRef = useRef<THREE.RectAreaLight>(null)
  const spillLightRef = useRef<THREE.PointLight>(null)
  const invalidate = useThree((state) => state.invalidate)
  const debugRuntime = useLightDebugRuntime(
    light.name,
    !light.hideRender,
    light.positionOffset,
    light.rotationOffset,
    light.defaultDirectMultiplier,
    light.defaultSpillMultiplier,
    1,
    light.defaultWidthMultiplier,
    light.defaultHeightMultiplier,
  )
  const baseColor = useMemo(() => new THREE.Color(...light.color), [light.color])
  const directTargetColor = useMemo(
    () =>
      getGameTintedLightColor(
        baseColor,
        gameLightingColor,
        GAME_LIGHT_DIRECT_TINT_STRENGTH[light.name] ?? 0,
      ),
    [baseColor, gameLightingColor, light.name],
  )
  const spillTargetColor = useMemo(
    () =>
      getGameTintedLightColor(
        baseColor,
        gameLightingColor,
        GAME_LIGHT_SPILL_TINT_STRENGTH[light.name] ?? 0,
      ).lerp(new THREE.Color(1, 1, 1), INDIRECT_LIGHT_WHITE_MIX),
    [baseColor, gameLightingColor, light.name],
  )
  const directAnimatedColorRef = useRef(directTargetColor.clone())
  const spillAnimatedColorRef = useRef(spillTargetColor.clone())
  const adjustedBlenderPosition = useMemo(
    () => addVector3(light.position, debugRuntime.positionOffset),
    [debugRuntime.positionOffset, light.position],
  )
  const position = useMemo(
    () => toGltfPosition(adjustedBlenderPosition),
    [adjustedBlenderPosition],
  )
  const quaternion = useMemo(() => {
    const baseQuaternion = new THREE.Quaternion(...light.quaternion)
    const rotationOffsetQuaternion = new THREE.Quaternion().setFromEuler(
      new THREE.Euler(
        THREE.MathUtils.degToRad(debugRuntime.rotationOffset[0]),
        THREE.MathUtils.degToRad(debugRuntime.rotationOffset[1]),
        THREE.MathUtils.degToRad(debugRuntime.rotationOffset[2]),
        'XYZ',
      ),
    )

    return baseQuaternion.multiply(rotationOffsetQuaternion)
  }, [debugRuntime.rotationOffset, light.quaternion])
  const width = light.size * ROOM_PREVIEW_SCALE * debugRuntime.widthMultiplier
  const height =
    light.sizeY * ROOM_PREVIEW_SCALE * debugRuntime.heightMultiplier
  const spillDistance =
    Math.max(width, height) * AREA_LIGHT_SPILL_DISTANCE_SCALE
  const helperPlaneSize = useMemo<[number, number]>(
    () => [
      clampHelperPlaneSize(0.18 * light.size * debugRuntime.widthMultiplier),
      clampHelperPlaneSize(0.18 * light.sizeY * debugRuntime.heightMultiplier),
    ],
    [
      debugRuntime.heightMultiplier,
      debugRuntime.widthMultiplier,
      light.size,
      light.sizeY,
    ],
  )
  const spillIntensity =
    light.energy *
    light.spillScale *
    AREA_LIGHT_SPILL_INTENSITY_SCALE *
    debugRuntime.areaSpillMultiplier
  const directIntensity =
    light.energy *
    light.directScale *
    AREA_LIGHT_DIRECT_INTENSITY_SCALE *
    debugRuntime.areaDirectMultiplier

  useEffect(() => {
    invalidate()
  }, [directTargetColor, invalidate, spillTargetColor])

  useFrame((_, delta) => {
    const shouldContinueDirectTransition = advanceAnimatedLightColor(
      directAnimatedColorRef.current,
      directTargetColor,
      delta,
    )
    const shouldContinueSpillTransition = advanceAnimatedLightColor(
      spillAnimatedColorRef.current,
      spillTargetColor,
      delta,
    )

    directLightRef.current?.color.copy(directAnimatedColorRef.current)
    spillLightRef.current?.color.copy(spillAnimatedColorRef.current)

    if (shouldContinueDirectTransition || shouldContinueSpillTransition) {
      invalidate()
    }
  })

  return (
    <>
      {debugRuntime.showHelpers && (
        <BlenderLightDebugHelper
          color={light.color}
          isLightEnabled={debugRuntime.isLightVisible}
          isSelected={debugRuntime.isSelected}
          planeSize={helperPlaneSize}
          position={position}
          quaternion={quaternion}
        />
      )}
      {debugRuntime.isLightVisible && (
        <>
          <rectAreaLight
            ref={directLightRef}
            color={directTargetColor}
            height={height}
            intensity={directIntensity}
            name={light.name}
            position={position}
            quaternion={quaternion}
            userData={{ nearbyAssets: light.nearbyAssets }}
            width={width}
          />
          {spillIntensity > 0 && (
            <pointLight
              ref={spillLightRef}
              color={spillTargetColor}
              decay={2}
              distance={spillDistance}
              intensity={spillIntensity}
              name={`${light.name} Cycles spill`}
              position={position}
            />
          )}
        </>
      )}
    </>
  )
}

function BlenderPointLightMesh({
  gameLightingColor,
  light,
}: {
  gameLightingColor: string | null
  light: RoomPreviewBlenderPointLight
}) {
  const pointLightRef = useRef<THREE.PointLight>(null)
  const invalidate = useThree((state) => state.invalidate)
  const debugRuntime = useLightDebugRuntime(
    light.name,
    !light.hideRender,
    light.positionOffset,
    ZERO_VECTOR3,
    1,
    1,
    light.defaultPointMultiplier,
    1,
    1,
    light.defaultPointRangeMultiplier,
  )
  const baseColor = useMemo(() => new THREE.Color(...light.color), [light.color])
  const targetColor = useMemo(
    () =>
      getGameTintedLightColor(
        baseColor,
        gameLightingColor,
        GAME_POINT_LIGHT_TINT_STRENGTH,
      ),
    [baseColor, gameLightingColor],
  )
  const animatedColorRef = useRef(targetColor.clone())
  const adjustedBlenderPosition = useMemo(
    () => addVector3(light.position, debugRuntime.positionOffset),
    [debugRuntime.positionOffset, light.position],
  )
  const position = useMemo(
    () => toGltfPosition(adjustedBlenderPosition),
    [adjustedBlenderPosition],
  )

  useEffect(() => {
    invalidate()
  }, [invalidate, targetColor])

  useFrame((_, delta) => {
    const shouldContinueTransition = advanceAnimatedLightColor(
      animatedColorRef.current,
      targetColor,
      delta,
    )

    pointLightRef.current?.color.copy(animatedColorRef.current)

    if (shouldContinueTransition) {
      invalidate()
    }
  })

  return (
    <>
      {debugRuntime.showHelpers && (
        <BlenderLightDebugHelper
          color={light.color}
          isLightEnabled={debugRuntime.isLightVisible}
          isSelected={debugRuntime.isSelected}
          position={position}
        />
      )}
      {debugRuntime.isLightVisible && (
        <pointLight
          ref={pointLightRef}
          color={targetColor}
          decay={2}
          distance={POINT_LIGHT_DISTANCE * debugRuntime.pointRangeMultiplier}
          intensity={
            light.energy *
            POINT_LIGHT_INTENSITY_SCALE *
            debugRuntime.pointMultiplier
          }
          name={light.name}
          position={position}
        />
      )}
    </>
  )
}

export default function RoomPreviewBlenderLights({
  enableGameLighting = false,
}: {
  enableGameLighting?: boolean
}) {
  const selectedGameIndex = useHubGameStore((state) => state.selectedGameIndex)
  const selectedGameLightingColor = enableGameLighting
    ? getHubGameLightingColor(selectedGameIndex)
    : null

  useEffect(() => {
    RectAreaLightUniformsLib.init()
  }, [])

  return (
    <>
      {ROOM_PREVIEW_BLENDER_AREA_LIGHTS.map((light) => (
        <BlenderAreaLightMesh
          key={light.name}
          gameLightingColor={selectedGameLightingColor}
          light={light}
        />
      ))}
      {ROOM_PREVIEW_BLENDER_POINT_LIGHTS.map((light) => (
        <BlenderPointLightMesh
          key={light.name}
          gameLightingColor={selectedGameLightingColor}
          light={light}
        />
      ))}
    </>
  )
}
