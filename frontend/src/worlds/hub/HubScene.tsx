import { Suspense, useRef } from 'react'
import { Group } from 'three'
import { SkyDome } from '@/components'
import {
  HUB_SKY_DOME_RADIUS,
  HUB_SKY_DOME_ROTATION_Y_OFFSET,
  HUB_SKY_DOME_SCALE,
  HUB_SKY_TEXTURE_OFFSET,
  HUB_SKY_TEXTURE_REPEAT,
} from './constants'
import { useHubViewportControls } from './hooks'
import HubPlatformMesh from './objects/HubPlatformMesh'
import NightStarFieldMesh from './objects/NightStarFieldMesh'

export default function HubScene() {
  const modelRootRef = useRef<Group>(null)
  const skyRootRef = useRef<Group>(null)
  useHubViewportControls(modelRootRef, skyRootRef)

  return (
    <>
      <Suspense fallback={null}>
        <group ref={skyRootRef}>
          <SkyDome
            radius={HUB_SKY_DOME_RADIUS}
            rotationY={HUB_SKY_DOME_ROTATION_Y_OFFSET}
            domeScale={HUB_SKY_DOME_SCALE}
            textureOffset={HUB_SKY_TEXTURE_OFFSET}
            textureRepeat={HUB_SKY_TEXTURE_REPEAT}
          />
        </group>
      </Suspense>
      <fog attach="fog" args={['#262449', 18, 34]} />
      <hemisphereLight args={['#fffef8', '#d7d3cd', 1.7]} />
      <directionalLight color="#ffffff" intensity={2.2} position={[6, 8, 8]} />
      <directionalLight color="#f2f7ff" intensity={1.2} position={[-8, 3, -4]} />
      <NightStarFieldMesh />
      <group ref={modelRootRef}>
        <Suspense fallback={null}>
          <HubPlatformMesh />
        </Suspense>
      </group>
    </>
  )
}
