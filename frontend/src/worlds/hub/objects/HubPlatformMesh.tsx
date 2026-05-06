import { Suspense } from 'react'
import CommunityCanvasBookMesh from './CommunityCanvasBookMesh'
import FlipbookBunnyMesh from './FlipbookBunnyMesh'
import {
  preloadHubPlatformModel,
  useDeferredHubAssetMount,
  useHubPlatformModel,
} from './hooks'
import RelayDrawingPlaceholderMesh from './RelayDrawingPlaceholderMesh'
import WitchMesh from './WitchMesh'

const DECORATIVE_ASSET_LOAD_DELAY_MS = 120

export default function HubPlatformMesh() {
  const preparedPlatform = useHubPlatformModel()
  const canMountDecorativeAssets = useDeferredHubAssetMount(DECORATIVE_ASSET_LOAD_DELAY_MS)

  return (
    <group position={preparedPlatform.position} scale={preparedPlatform.scale}>
      <primitive object={preparedPlatform.platform} dispose={null} />
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
            <WitchMesh />
          </Suspense>
        </>
      )}
    </group>
  )
}

preloadHubPlatformModel()
