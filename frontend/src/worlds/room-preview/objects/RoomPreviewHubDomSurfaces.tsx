import type * as THREE from 'three'
import CommunityCanvasWhiteboardPreviewMesh from '@/worlds/hub/objects/CommunityCanvasWhiteboardPreviewMesh'
import { useCommunityCanvasWhiteboardAssetNavigation } from '@/worlds/hub/objects/hooks'
import MonitorGameSelector from '@/worlds/hub/objects/MonitorGameSelector'

const STAGE6_MONITOR_DOM_POSITION: [number, number, number] = [
  -0.21411,
  0.47817,
  -0.6362,
]

const STAGE6_MONITOR_DOM_QUATERNION: [number, number, number, number] = [
  0,
  -0.006045,
  0,
  0.999982,
]

const STAGE6_MONITOR_DOM_SCALE = 0.1429

const STAGE6_WHITEBOARD_DOM_POSITION: [number, number, number] = [
  -0.648,
  0.55529,
  -0.10868,
]

const STAGE6_WHITEBOARD_DOM_QUATERNION: [number, number, number, number] = [
  0,
  -0.707107,
  0,
  0.707107,
]

export default function RoomPreviewHubDomSurfaces({
  scene,
}: {
  scene: THREE.Object3D
}) {
  const isWhiteboardHovered = useCommunityCanvasWhiteboardAssetNavigation(scene)

  return (
    <>
      <MonitorGameSelector
        position={STAGE6_MONITOR_DOM_POSITION}
        quaternion={STAGE6_MONITOR_DOM_QUATERNION}
        scale={STAGE6_MONITOR_DOM_SCALE}
      />
      <CommunityCanvasWhiteboardPreviewMesh
        flipContentX
        isExpanded={isWhiteboardHovered}
        position={STAGE6_WHITEBOARD_DOM_POSITION}
        quaternion={STAGE6_WHITEBOARD_DOM_QUATERNION}
      />
    </>
  )
}
