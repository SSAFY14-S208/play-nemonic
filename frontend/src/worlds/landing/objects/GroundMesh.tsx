import { RigidBody } from '@react-three/rapier'
import * as THREE from 'three'

const GROUND_SIZE = 50
// 따뜻한 나무 바닥 색상 (레퍼런스 2 책상 질감)
const WOOD_COLOR = new THREE.Color('#c8a87a')

export default function GroundMesh() {
  return (
    <RigidBody type="fixed" friction={0.8}>
      <mesh
        rotation={[-Math.PI / 2, 0, 0]}
        receiveShadow
      >
        <planeGeometry args={[GROUND_SIZE, GROUND_SIZE]} />
        <meshStandardMaterial
          color={WOOD_COLOR}
          roughness={0.85}
          metalness={0.05}
        />
      </mesh>
    </RigidBody>
  )
}
