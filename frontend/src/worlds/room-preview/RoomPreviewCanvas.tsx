'use client'

import { Canvas } from '@react-three/fiber'
import * as THREE from 'three'
import { cn } from '@/shared/libs'
import {
  ROOM_PREVIEW_CAMERA,
  ROOM_PREVIEW_HUB_CAMERA_PRESETS,
  ROOM_PREVIEW_RENDERING,
  type RoomPreviewVariant,
} from './constants'
import RoomPreviewScene from './RoomPreviewScene'

export default function RoomPreviewCanvas({
  className,
  onCanvasReady,
  variant = 'preview',
}: {
  className?: string
  onCanvasReady?: () => void
  variant?: RoomPreviewVariant
}) {
  const initialCamera =
    variant === 'hub' ? ROOM_PREVIEW_HUB_CAMERA_PRESETS.overview : ROOM_PREVIEW_CAMERA

  return (
    <Canvas
      className={cn('absolute inset-0 h-full w-full', className)}
      camera={{
        fov: ROOM_PREVIEW_CAMERA.fov,
        near: ROOM_PREVIEW_CAMERA.near,
        far: ROOM_PREVIEW_CAMERA.far,
        position: initialCamera.position,
      }}
      dpr={ROOM_PREVIEW_RENDERING.devicePixelRatio}
      gl={{
        alpha: false,
        antialias: true,
        powerPreference: 'high-performance',
      }}
      shadows
      onCreated={({ camera, gl }) => {
        camera.lookAt(...initialCamera.target)
        gl.outputColorSpace = THREE.SRGBColorSpace
        gl.toneMapping = THREE.AgXToneMapping
        gl.toneMappingExposure = ROOM_PREVIEW_RENDERING.toneMappingExposure
        gl.shadowMap.enabled = true
        gl.shadowMap.type = THREE.PCFSoftShadowMap
        onCanvasReady?.()
      }}
    >
      <RoomPreviewScene variant={variant} />
    </Canvas>
  )
}
