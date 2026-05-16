import { Suspense } from 'react'
import { Environment } from '@react-three/drei'
import { HUB_PERFORMANCE_PROFILES } from '@/shared/constants'
import type { HubPerformanceMode } from '@/shared/types'
import BlenderRoomLights from './objects/BlenderRoomLights'

export default function LightingSetup({
  performanceMode,
}: {
  performanceMode: HubPerformanceMode
}) {
  const performanceProfile = HUB_PERFORMANCE_PROFILES[performanceMode]
  const isQualityMode = performanceMode === 'quality'

  if (!performanceProfile.environment) {
    return (
      <>
        <ambientLight color="#f8f3ff" intensity={0.72} />
        <directionalLight
          color="#fff8f0"
          intensity={1.08}
          position={[4, 6, 4]}
        />
      </>
    )
  }

  return (
    <>
      <Suspense fallback={null}>
        <Environment
          background={performanceProfile.environmentBackground}
          backgroundBlurriness={performanceProfile.environmentBackgroundBlurriness}
          backgroundIntensity={performanceProfile.environmentBackgroundIntensity}
          backgroundRotation={performanceProfile.environmentBackgroundRotation}
          environmentIntensity={performanceProfile.environmentIntensity}
          environmentRotation={performanceProfile.environmentRotation}
          preset={performanceProfile.environmentPreset}
        />
      </Suspense>
      <hemisphereLight
        args={['#fff9fb', '#dbe8ff', isQualityMode ? 0.16 : 0.22]}
      />
      <ambientLight
        color="#f8f1ff"
        intensity={isQualityMode ? 0.035 : 0.06}
      />
      <directionalLight
        castShadow={performanceProfile.shadows}
        color="#fff3e4"
        intensity={performanceProfile.shadows ? 0.18 : 0.42}
        position={[4.6, 7.2, 5.4]}
        shadow-bias={-0.00018}
        shadow-mapSize-height={performanceProfile.shadowMapSize}
        shadow-mapSize-width={performanceProfile.shadowMapSize}
      />
      <BlenderRoomLights
        mode={performanceMode === 'quality' ? 'quality' : 'balanced'}
      />
    </>
  )
}
