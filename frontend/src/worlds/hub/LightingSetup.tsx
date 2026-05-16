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
        <ambientLight color="#f7f1ff" intensity={0.7} />
        <directionalLight
          color="#fff6ec"
          intensity={1.15}
          position={[4, 6, 4]}
        />
      </>
    )
  }

  return (
    <>
      <Suspense fallback={null}>
        <Environment
          preset="apartment"
          environmentIntensity={isQualityMode ? 0.2 : 0.16}
        />
      </Suspense>
      <hemisphereLight
        args={['#fff4ff', '#d8e4ff', isQualityMode ? 0.28 : 0.22]}
      />
      <ambientLight
        color="#f1e8ff"
        intensity={isQualityMode ? 0.075 : 0.06}
      />
      <directionalLight
        castShadow={performanceProfile.shadows}
        color="#fff6ec"
        intensity={performanceProfile.shadows ? 0.5 : 0.72}
        position={[4.6, 7.2, 5.4]}
        shadow-bias={-0.00018}
        shadow-mapSize-height={1024}
        shadow-mapSize-width={1024}
      />
      <BlenderRoomLights
        mode={performanceMode === 'quality' ? 'quality' : 'balanced'}
      />
    </>
  )
}
