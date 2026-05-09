import { Suspense, useRef } from 'react'
import type { Group } from 'three'
import { SkyDome } from '@/components'
import HubLighting from '@/worlds/_infra/HubLighting'
import {
  HUB_SKY_DOME_RADIUS,
  HUB_SKY_DOME_ROTATION_Y_OFFSET,
  HUB_SKY_DOME_SCALE,
  HUB_SKY_TEXTURE_OFFSET,
  HUB_SKY_TEXTURE_REPEAT,
} from './constants'
import { useHubViewportControls } from './hooks'
import HubPlatformGroup from './objects/HubPlatformGroup'
import NightStarFieldMesh from './objects/NightStarFieldMesh'
import ThreeWaterMesh from './objects/ThreeWaterMesh'

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
      <HubLighting />
      <NightStarFieldMesh />
      <Suspense fallback={null}>
        <ThreeWaterMesh />
      </Suspense>
      <Suspense fallback={null}>
        <HubPlatformGroup modelRootRef={modelRootRef} />
      </Suspense>
    </>
  )
}
