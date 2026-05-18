import {
  CameraControls,
  Environment,
  OrbitControls,
} from '@react-three/drei'
import { useThree, type ThreeEvent } from '@react-three/fiber'
import { useRouter } from 'next/navigation'
import {
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  type ElementRef,
} from 'react'
import * as THREE from 'three'
import { useHubRoomStore } from '@/shared/stores'
import {
  ROOM_PREVIEW_CAMERA,
  ROOM_PREVIEW_CONTROLS,
  ROOM_PREVIEW_HUB_CAMERA_LIMITS,
  ROOM_PREVIEW_HUB_CAMERA_PRESETS,
  ROOM_PREVIEW_LIGHTING,
  ROOM_PREVIEW_RENDERING,
  type RoomPreviewVariant,
} from './constants'
import { useRoomPreviewLightDebugStore } from './light-debug'
import RoomPreviewBlenderLights from './objects/RoomPreviewBlenderLights'
import RoomPreviewHubDomSurfaces from './objects/RoomPreviewHubDomSurfaces'
import RoomPreviewModel from './objects/RoomPreviewModel'
import RoomPreviewPostProcessing from './RoomPreviewPostProcessing'

const NEMONIC_SINGLE_ROOM_PATH = '/hub'
const NEMONIC_DEVICE_OBJECT_NAME_PREFIX = 'NEMONIC_'

function isNemonicDeviceObject(object: THREE.Object3D) {
  return object.name.startsWith(NEMONIC_DEVICE_OBJECT_NAME_PREFIX)
}

function RoomPreviewHubCameraRig() {
  const controlsRef = useRef<ElementRef<typeof CameraControls>>(null)
  const focusKey = useHubRoomStore((state) => state.focusKey)
  const invalidate = useThree((state) => state.invalidate)
  const isLightDebugEnabled = useRoomPreviewLightDebugStore(
    (state) => state.isDebugEnabled,
  )
  const roomBoundary = useMemo(
    () =>
      new THREE.Box3(
        new THREE.Vector3(...ROOM_PREVIEW_HUB_CAMERA_LIMITS.boundary.min),
        new THREE.Vector3(...ROOM_PREVIEW_HUB_CAMERA_LIMITS.boundary.max),
      ),
    [],
  )
  const debugBoundary = useMemo(
    () =>
      new THREE.Box3(
        new THREE.Vector3(-80, -80, -80),
        new THREE.Vector3(80, 80, 80),
      ),
    [],
  )
  const invalidateCanvas = useCallback(() => {
    invalidate()
  }, [invalidate])

  useEffect(() => {
    const controls = controlsRef.current
    if (!controls) return

    controls.setBoundary(isLightDebugEnabled ? debugBoundary : roomBoundary)
    controls.boundaryEnclosesCamera = !isLightDebugEnabled
    controls.boundaryFriction = isLightDebugEnabled
      ? 0
      : ROOM_PREVIEW_HUB_CAMERA_LIMITS.boundaryFriction
    invalidate()
  }, [debugBoundary, invalidate, isLightDebugEnabled, roomBoundary])

  useEffect(() => {
    const controls = controlsRef.current
    if (!controls) return

    const preset =
      ROOM_PREVIEW_HUB_CAMERA_PRESETS[focusKey] ??
      ROOM_PREVIEW_HUB_CAMERA_PRESETS.overview

    void controls.setLookAt(
      preset.position[0],
      preset.position[1],
      preset.position[2],
      preset.target[0],
      preset.target[1],
      preset.target[2],
      true,
    )
    invalidate()
  }, [focusKey, invalidate])

  return (
    <CameraControls
      ref={controlsRef}
      makeDefault
      azimuthRotateSpeed={isLightDebugEnabled ? 1 : 0.55}
      draggingSmoothTime={0.12}
      dollySpeed={isLightDebugEnabled ? 1.2 : 0.5}
      maxAzimuthAngle={
        isLightDebugEnabled
          ? Infinity
          : ROOM_PREVIEW_HUB_CAMERA_LIMITS.maxAzimuthAngle
      }
      maxDistance={
        isLightDebugEnabled ? 80 : ROOM_PREVIEW_HUB_CAMERA_LIMITS.maxDistance
      }
      maxPolarAngle={
        isLightDebugEnabled
          ? Math.PI - 0.01
          : ROOM_PREVIEW_HUB_CAMERA_LIMITS.maxPolarAngle
      }
      minAzimuthAngle={
        isLightDebugEnabled
          ? -Infinity
          : ROOM_PREVIEW_HUB_CAMERA_LIMITS.minAzimuthAngle
      }
      minDistance={
        isLightDebugEnabled ? 0.1 : ROOM_PREVIEW_HUB_CAMERA_LIMITS.minDistance
      }
      minPolarAngle={
        isLightDebugEnabled
          ? 0.01
          : ROOM_PREVIEW_HUB_CAMERA_LIMITS.minPolarAngle
      }
      polarRotateSpeed={isLightDebugEnabled ? 1 : 0.42}
      smoothTime={0.36}
      truckSpeed={isLightDebugEnabled ? 1 : 0}
      onControl={invalidateCanvas}
      onRest={invalidateCanvas}
      onSleep={invalidateCanvas}
    />
  )
}

function RoomPreviewCameraControls({
  variant,
}: {
  variant: RoomPreviewVariant
}) {
  const isLightDebugEnabled = useRoomPreviewLightDebugStore(
    (state) => state.isDebugEnabled,
  )

  if (variant === 'hub') return <RoomPreviewHubCameraRig />

  return (
    <OrbitControls
      enableDamping
      dampingFactor={ROOM_PREVIEW_CONTROLS.dampingFactor}
      makeDefault
      maxDistance={isLightDebugEnabled ? 80 : ROOM_PREVIEW_CONTROLS.maxDistance}
      maxPolarAngle={
        isLightDebugEnabled ? Math.PI - 0.01 : ROOM_PREVIEW_CONTROLS.maxPolarAngle
      }
      minDistance={isLightDebugEnabled ? 0.1 : ROOM_PREVIEW_CONTROLS.minDistance}
      minPolarAngle={isLightDebugEnabled ? 0.01 : undefined}
      target={ROOM_PREVIEW_CAMERA.target}
    />
  )
}

export default function RoomPreviewScene({
  variant = 'preview',
}: {
  variant?: RoomPreviewVariant
}) {
  const router = useRouter()
  const handleHubModelClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      if (!isNemonicDeviceObject(event.object)) return

      event.stopPropagation()
      document.body.style.cursor = ''
      router.push(NEMONIC_SINGLE_ROOM_PATH)
    },
    [router],
  )
  const handleHubModelPointerOver = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      if (!isNemonicDeviceObject(event.object)) return

      event.stopPropagation()
      document.body.style.cursor = 'pointer'
    },
    [],
  )
  const handleHubModelPointerOut = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      if (!isNemonicDeviceObject(event.object)) return

      document.body.style.cursor = ''
    },
    [],
  )
  const isHubVariant = variant === 'hub'
  const isLightDebugEnabled = useRoomPreviewLightDebugStore(
    (state) => state.isDebugEnabled,
  )
  const globalFillMultiplier = useRoomPreviewLightDebugStore(
    (state) => state.globalFillMultiplier,
  )
  const fillMultiplier = isLightDebugEnabled ? globalFillMultiplier : 1

  return (
    <>
      <color
        attach="background"
        args={[ROOM_PREVIEW_RENDERING.backgroundColor]}
      />
      <fog
        attach="fog"
        args={[
          ROOM_PREVIEW_RENDERING.backgroundColor,
          ROOM_PREVIEW_RENDERING.fogNear,
          ROOM_PREVIEW_RENDERING.fogFar,
        ]}
      />
      <Environment
        environmentIntensity={
          ROOM_PREVIEW_LIGHTING.environment.intensity * fillMultiplier
        }
        preset={ROOM_PREVIEW_LIGHTING.environment.preset}
      />
      <ambientLight
        color={ROOM_PREVIEW_LIGHTING.ambient.color}
        intensity={ROOM_PREVIEW_LIGHTING.ambient.intensity * fillMultiplier}
      />
      <hemisphereLight
        args={[
          ROOM_PREVIEW_LIGHTING.hemisphere.skyColor,
          ROOM_PREVIEW_LIGHTING.hemisphere.groundColor,
          ROOM_PREVIEW_LIGHTING.hemisphere.intensity * fillMultiplier,
        ]}
      />
      <RoomPreviewModel
        onClick={isHubVariant ? handleHubModelClick : undefined}
        onPointerOut={isHubVariant ? handleHubModelPointerOut : undefined}
        onPointerOver={isHubVariant ? handleHubModelPointerOver : undefined}
      >
        {(modelScene) => (
          <>
            <RoomPreviewBlenderLights />
            {isHubVariant && !isLightDebugEnabled && (
              <Suspense fallback={null}>
                <RoomPreviewHubDomSurfaces scene={modelScene} />
              </Suspense>
            )}
          </>
        )}
      </RoomPreviewModel>
      <RoomPreviewCameraControls variant={variant} />
      <RoomPreviewPostProcessing />
    </>
  )
}
