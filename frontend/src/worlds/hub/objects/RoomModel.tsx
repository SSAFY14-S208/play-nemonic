import { Suspense, useRef } from 'react'
import { useAnimations, useGLTF } from '@react-three/drei'
import * as THREE from 'three'
import {
  HUB_ROOM_MODEL_PATH,
  HUB_ROOM_POSITION,
  HUB_ROOM_SCALE,
} from '@/shared/constants'
import type { HubPerformanceMode } from '@/shared/types'
import { extendRoomGltfLoader } from '../hubGltfLoader'
import CarpetFurMesh from './CarpetFurMesh'
import CommunityCanvasWhiteboardPreviewMesh from './CommunityCanvasWhiteboardPreviewMesh'
import TabletopSheenMesh from './TabletopSheenMesh'
import { useCommunityCanvasWhiteboardAssetNavigation, useRoomModel } from './hooks'

export default function RoomModel({
  performanceMode,
}: {
  performanceMode: HubPerformanceMode
}) {
  const groupRef = useRef<THREE.Group>(null)
  const { scene, animations } = useGLTF(
    HUB_ROOM_MODEL_PATH,
    true,
    true,
    extendRoomGltfLoader,
  )
  const { actions } = useAnimations(animations, groupRef)

  useRoomModel(scene, animations, actions, performanceMode)
  const isWhiteboardHovered = useCommunityCanvasWhiteboardAssetNavigation(scene)

  return (
    <group
      ref={groupRef}
      position={HUB_ROOM_POSITION}
      scale={HUB_ROOM_SCALE}
    >
      <primitive
        object={scene}
        dispose={null}
      />
      <Suspense fallback={null}>
        <CommunityCanvasWhiteboardPreviewMesh isExpanded={isWhiteboardHovered} />
      </Suspense>
      <TabletopSheenMesh performanceMode={performanceMode} />
      <CarpetFurMesh performanceMode={performanceMode} />
    </group>
  )
}

useGLTF.preload(HUB_ROOM_MODEL_PATH, true, true, extendRoomGltfLoader)
