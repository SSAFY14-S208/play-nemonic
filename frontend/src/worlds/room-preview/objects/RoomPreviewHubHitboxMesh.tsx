import { Edges } from '@react-three/drei'
import type { ThreeEvent } from '@react-three/fiber'
import * as THREE from 'three'
import type { RoomPreviewHubHitboxConfig } from '../useRoomPreviewHubHitboxCalibration'

interface RoomPreviewHubHitboxMeshProps {
  color: string
  config: RoomPreviewHubHitboxConfig
  isVisible: boolean
  name: string
  onClick?: (event: ThreeEvent<MouseEvent>) => void
  onPointerEnter?: (event: ThreeEvent<PointerEvent>) => void
  onPointerLeave?: (event: ThreeEvent<PointerEvent>) => void
}

export default function RoomPreviewHubHitboxMesh({
  color,
  config,
  isVisible,
  name,
  onClick,
  onPointerEnter,
  onPointerLeave,
}: RoomPreviewHubHitboxMeshProps) {
  const rotation = config.rotation.map(THREE.MathUtils.degToRad) as [
    number,
    number,
    number,
  ]

  const handlePointerDown = (event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation()
  }

  return (
    <mesh
      name={`ROOM_PREVIEW_HITBOX_${name}`}
      position={config.position}
      renderOrder={140}
      rotation={rotation}
      onClick={onClick}
      onPointerDown={handlePointerDown}
      onPointerEnter={onPointerEnter}
      onPointerLeave={onPointerLeave}
    >
      <boxGeometry args={config.size} />
      <meshBasicMaterial
        color={color}
        colorWrite={isVisible}
        depthTest={false}
        depthWrite={false}
        opacity={isVisible ? 0.22 : 0}
        side={THREE.DoubleSide}
        transparent
      />
      {isVisible && <Edges color={color} linewidth={2} />}
    </mesh>
  )
}
