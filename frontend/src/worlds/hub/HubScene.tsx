import { Suspense, useRef } from 'react'
import { Group } from 'three'
import HubPlatformMesh from './objects/HubPlatformMesh'
import NightStarFieldMesh from './objects/NightStarFieldMesh'
import { useHubViewportControls } from './useHubViewportControls'

export default function HubScene() {
  const modelRootRef = useRef<Group>(null)
  useHubViewportControls(modelRootRef)

  return (
    <>
      <fog attach="fog" args={['#262449', 18, 34]} />
      <hemisphereLight args={['#fffef8', '#d7d3cd', 1.7]} />
      <directionalLight color="#ffffff" intensity={2.2} position={[6, 8, 8]} />
      <directionalLight color="#f2f7ff" intensity={1.2} position={[-8, 3, -4]} />
      <NightStarFieldMesh />
      <mesh position={[0, -2.58, 0]}>
        <cylinderGeometry args={[4.5, 5.5, 0.25, 96]} />
        <meshStandardMaterial color="#ece8de" roughness={0.95} metalness={0} />
      </mesh>
      <group ref={modelRootRef}>
        <Suspense fallback={null}>
          <HubPlatformMesh />
        </Suspense>
      </group>
    </>
  )
}
