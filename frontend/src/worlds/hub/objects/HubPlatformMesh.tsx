import CenterNemonicMesh from './CenterNemonicMesh'
import CommunityCanvasBookMesh from './CommunityCanvasBookMesh'
import { preloadHubPlatformModel, useHubPlatformModel } from './hooks'
import WitchMesh from './WitchMesh'

export default function HubPlatformMesh() {
  const preparedPlatform = useHubPlatformModel()

  return (
    <group position={preparedPlatform.position} scale={preparedPlatform.scale}>
      <primitive object={preparedPlatform.platform} dispose={null} />
      <CommunityCanvasBookMesh />
      <CenterNemonicMesh />
      <WitchMesh />
    </group>
  )
}

preloadHubPlatformModel()
