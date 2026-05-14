import * as THREE from 'three'
import {
  HUB_PEGBOARD_DROP_CENTER,
  HUB_PEGBOARD_DROP_SIZE,
  HUB_WORKSPACE_DROP_CENTER,
  HUB_WORKSPACE_DROP_SIZE,
} from '@/shared/constants'
import { usePegboardArea } from './hooks'

export default function PegboardAreaMesh() {
  const {
    focusPegboard,
    focusWorkspace,
    handlePointerEnter,
    handlePointerLeave,
  } = usePegboardArea()

  return (
    <group>
      <mesh
        position={HUB_WORKSPACE_DROP_CENTER}
        rotation={[-Math.PI / 2, 0, 0]}
        userData={{ hubDropSurface: 'workspace' }}
        onClick={focusWorkspace}
        onPointerEnter={handlePointerEnter}
        onPointerLeave={handlePointerLeave}
      >
        <planeGeometry args={HUB_WORKSPACE_DROP_SIZE} />
        <meshBasicMaterial
          color="#d9c9ff"
          depthWrite={false}
          opacity={0.035}
          side={THREE.DoubleSide}
          transparent
        />
      </mesh>

      <mesh
        position={HUB_PEGBOARD_DROP_CENTER}
        userData={{
          futureEntry: 'community-canvas',
          hubDropSurface: 'pegboard',
        }}
        onClick={focusPegboard}
        onPointerEnter={handlePointerEnter}
        onPointerLeave={handlePointerLeave}
      >
        <planeGeometry args={HUB_PEGBOARD_DROP_SIZE} />
        <meshBasicMaterial
          color="#bfe8ff"
          depthWrite={false}
          opacity={0.055}
          side={THREE.DoubleSide}
          transparent
        />
      </mesh>
    </group>
  )
}
