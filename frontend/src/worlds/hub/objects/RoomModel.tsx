import { useRef } from 'react'
import { useAnimations, useGLTF } from '@react-three/drei'
import * as THREE from 'three'
import {
  HUB_ROOM_MODEL_PATH,
  HUB_ROOM_POSITION,
  HUB_ROOM_SCALE,
} from '@/shared/constants'
import type { HubPerformanceMode } from '@/shared/types'
import CarpetFurMesh from './CarpetFurMesh'
import { useRoomModel } from './hooks'

export default function RoomModel({
  performanceMode,
}: {
  performanceMode: HubPerformanceMode
}) {
  const groupRef = useRef<THREE.Group>(null)
  const { scene, animations } = useGLTF(HUB_ROOM_MODEL_PATH)
  const { actions } = useAnimations(animations, groupRef)

  useRoomModel(scene, animations, actions, performanceMode)

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
      <CarpetFurMesh performanceMode={performanceMode} />
    </group>
  )
}

useGLTF.preload(HUB_ROOM_MODEL_PATH)
