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
        <Environment preset="apartment" environmentIntensity={0.16} />
      </Suspense>
      <hemisphereLight args={['#fff0ff', '#b8e5ff', 0.28]} />
      <ambientLight color="#ccb5ff" intensity={0.07} />
      <directionalLight
        castShadow={performanceProfile.shadows}
        color="#fff6ec"
        intensity={performanceProfile.shadows ? 0.48 : 0.72}
        position={[4.6, 7.2, 5.4]}
        shadow-bias={-0.00018}
        shadow-mapSize-height={1024}
        shadow-mapSize-width={1024}
      />
      {performanceMode === 'quality' && <BlenderRoomLights />}
    </>
  )
}
