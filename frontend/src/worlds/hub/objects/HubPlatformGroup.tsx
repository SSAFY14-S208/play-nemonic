import { Suspense, type RefObject } from 'react'
import type { Group } from 'three'
import { HUB_DECORATIVE_ASSET_LOAD_DELAY_MS } from '../constants'
import CommunityCanvasBookMesh from './CommunityCanvasBookMesh'
import FlipbookBunnyMesh from './FlipbookBunnyMesh'
import HubPlatformMesh from './HubPlatformMesh'
import {
  useDeferredHubAssetMount,
  useHubPlatformModel,
} from './hooks'
import RelayDrawingPlaceholderMesh from './RelayDrawingPlaceholderMesh'
import WitchMesh from './WitchMesh'

interface HubPlatformGroupProps {
  modelRootRef: RefObject<Group | null>
}

export default function HubPlatformGroup({ modelRootRef }: HubPlatformGroupProps) {
  const preparedPlatform = useHubPlatformModel()
  const canMountDecorativeAssets = useDeferredHubAssetMount(HUB_DECORATIVE_ASSET_LOAD_DELAY_MS)

  return (
    <group
      ref={modelRootRef}
      position={preparedPlatform.position}
      scale={preparedPlatform.scale}
    >
      <HubPlatformMesh platform={preparedPlatform.platform} />
      <RelayDrawingPlaceholderMesh />
      {canMountDecorativeAssets && (
        <>
          <Suspense fallback={null}>
            <CommunityCanvasBookMesh />
          </Suspense>
          <Suspense fallback={null}>
            <FlipbookBunnyMesh />
          </Suspense>
          <Suspense fallback={null}>
            <WitchMesh contentKey="fortune" />
          </Suspense>
        </>
      )}
    </group>
  )
}
