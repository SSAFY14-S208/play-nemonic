import { useRef } from 'react'
import { RigidBody, CapsuleCollider, type RapierRigidBody } from '@react-three/rapier'

export default function Character() {
  const rigidBodyRef = useRef<RapierRigidBody>(null)

  return (
    <RigidBody ref={rigidBodyRef} type="kinematicPosition" colliders={false}>
      <CapsuleCollider args={[0.4, 0.4]} />
      <group>{/* 캐릭터 모델 */}</group>
    </RigidBody>
  )
}
