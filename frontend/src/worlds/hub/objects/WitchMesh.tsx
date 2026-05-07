import { useRef } from 'react'
import type { Group } from 'three'
import type { HubContentKey } from '@/shared/stores'
import {
  HUB_WITCH_PLATFORM_POSITION,
  HUB_WITCH_PLATFORM_ROTATION_Y,
} from '../constants'
import {
  useWitchHoverMotion,
  useWitchInteraction,
  useWitchModel,
} from './hooks'

interface WitchMeshProps {
  contentKey?: HubContentKey
}

export default function WitchMesh({ contentKey = 'fortune' }: WitchMeshProps) {
  const wrapperRef = useRef<Group>(null)
  const { model, shadow } = useWitchModel()
  const {
    handleWitchClick,
    handleWitchPointerEnter,
    handleWitchPointerLeave,
  } = useWitchInteraction(contentKey)

  useWitchHoverMotion(wrapperRef, shadow)

  return (
    <group
      ref={wrapperRef}
      position={HUB_WITCH_PLATFORM_POSITION}
      rotation={[0, HUB_WITCH_PLATFORM_ROTATION_Y, 0]}
    >
      <primitive object={shadow} />
      <pointLight
        position={[0, 2.25, 0.25]}
        color="#c7a7ff"
        intensity={0.9}
        distance={5}
      />
      <mesh
        position={[0, 1.62, -0.08]}
        onPointerEnter={handleWitchPointerEnter}
        onPointerLeave={handleWitchPointerLeave}
        onClick={handleWitchClick}
      >
        <boxGeometry args={[3.5, 3.55, 2.6]} />
        <meshBasicMaterial
          transparent
          opacity={0}
          depthWrite={false}
          colorWrite={false}
        />
      </mesh>
      <primitive object={model} dispose={null} />
    </group>
  )
}
