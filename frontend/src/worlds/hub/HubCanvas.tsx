'use client'

import { Canvas } from '@react-three/fiber'
import { useEffect } from 'react'
import * as THREE from 'three'
import {
  HUB_CAMERA_PRESETS,
  HUB_PERFORMANCE_PROFILES,
} from '@/shared/constants'
import type { HubPerformanceMode } from '@/shared/types'
import { startHubPerformanceDiagnostics } from '@/shared/utils'
import HubScene from './HubScene'

export default function HubCanvas({
  onCanvasReady,
  performanceMode,
}: {
  onCanvasReady?: () => void
  performanceMode: HubPerformanceMode
}) {
  const overviewCamera = HUB_CAMERA_PRESETS.overview
  const performanceProfile = HUB_PERFORMANCE_PROFILES[performanceMode]

  useEffect(() => {
    return startHubPerformanceDiagnostics(performanceMode)
  }, [performanceMode])

  return (
    <Canvas
      frameloop="demand"
      shadows={performanceProfile.shadows}
      className="absolute inset-0 z-[1] h-full w-full cursor-grab active:cursor-grabbing"
      camera={{
        position: overviewCamera.position,
        fov: 64,
        near: 0.1,
        far: 80,
      }}
      dpr={performanceProfile.dpr}
      gl={{
        antialias: true,
        alpha: false,
        powerPreference: 'high-performance',
      }}
      onCreated={({ camera, gl }) => {
        camera.lookAt(
          overviewCamera.target[0],
          overviewCamera.target[1],
          overviewCamera.target[2],
        )
        gl.outputColorSpace = THREE.SRGBColorSpace
        gl.toneMapping = THREE.ACESFilmicToneMapping
        gl.toneMappingExposure = performanceProfile.toneMappingExposure
        gl.shadowMap.enabled = performanceProfile.shadows
        gl.shadowMap.type = THREE.PCFSoftShadowMap
        onCanvasReady?.()
      }}
    >
      <HubScene performanceMode={performanceMode} />
    </Canvas>
  )
}
