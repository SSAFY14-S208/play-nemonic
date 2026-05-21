import * as THREE from 'three'
import {
  HUB_WORKSPACE_DROP_CENTER,
  HUB_WORKSPACE_DROP_SIZE,
} from '@/shared/constants'
import { usePegboardArea } from './hooks'

export default function PegboardAreaMesh() {
  const {
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
          colorWrite={false}
          depthWrite={false}
          opacity={0}
          side={THREE.DoubleSide}
          transparent
        />
      </mesh>
    </group>
  )
}
