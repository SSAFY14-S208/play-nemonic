import { useEffect } from 'react'
import {
  Bloom,
  BrightnessContrast,
  EffectComposer,
  HueSaturation,
  SSAO,
} from '@react-three/postprocessing'
import { useThree } from '@react-three/fiber'
import * as THREE from 'three'
import { HUB_PERFORMANCE_PROFILES } from '@/shared/constants'
import type { HubPerformanceMode } from '@/shared/types'

export default function HubPostProcessing({
  performanceMode,
}: {
  performanceMode: HubPerformanceMode
}) {
  const invalidate = useThree((state) => state.invalidate)
  const postProcessingProfile =
    HUB_PERFORMANCE_PROFILES[performanceMode].postProcessing

  useEffect(() => {
    if (!postProcessingProfile.enabled) return

    invalidate()
  }, [invalidate, postProcessingProfile.enabled])

  if (!postProcessingProfile.enabled) return null

  const effects = []
  const ambientOcclusionProfile = postProcessingProfile.ambientOcclusion
  const bloomProfile = postProcessingProfile.bloom
  const colorGradeProfile = postProcessingProfile.colorGrade

  if (ambientOcclusionProfile) {
    effects.push(
      <SSAO
        key="ambient-occlusion"
        color={new THREE.Color(ambientOcclusionProfile.color)}
        distanceFalloff={ambientOcclusionProfile.distanceFalloff}
        intensity={ambientOcclusionProfile.intensity}
        luminanceInfluence={0.58}
        radius={ambientOcclusionProfile.aoRadius}
        resolutionScale={ambientOcclusionProfile.halfRes ? 0.5 : 1}
        rings={ambientOcclusionProfile.quality === 'medium' ? 5 : 4}
        samples={ambientOcclusionProfile.quality === 'medium' ? 16 : 8}
      />,
    )
  }

  if (bloomProfile) {
    effects.push(
      <Bloom
        key="bloom"
        intensity={bloomProfile.intensity}
        luminanceSmoothing={bloomProfile.luminanceSmoothing}
        luminanceThreshold={bloomProfile.luminanceThreshold}
        mipmapBlur={bloomProfile.mipmapBlur}
        radius={bloomProfile.radius}
      />,
    )
  }

  if (colorGradeProfile) {
    effects.push(
      <BrightnessContrast
        key="brightness-contrast"
        brightness={colorGradeProfile.brightness}
        contrast={colorGradeProfile.contrast}
      />,
      <HueSaturation
        key="hue-saturation"
        hue={0}
        saturation={colorGradeProfile.saturation}
      />,
    )
  }

  return (
    <EffectComposer
      depthBuffer
      enableNormalPass={Boolean(ambientOcclusionProfile)}
      multisampling={postProcessingProfile.multisampling}
      renderPriority={0}
      resolutionScale={postProcessingProfile.resolutionScale}
    >
      {effects}
    </EffectComposer>
  )
}
