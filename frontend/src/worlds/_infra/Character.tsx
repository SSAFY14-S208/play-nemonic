import { useRef, useEffect } from 'react'
import { useGLTF, useAnimations } from '@react-three/drei'
import { RigidBody, CapsuleCollider, type RapierRigidBody } from '@react-three/rapier'
import { useFrame } from '@react-three/fiber'
import * as THREE from 'three'
import { useCharacterMovement } from './hooks'

const MODEL_PATH = '/models/nong_dam_gom.glb'
const CAPSULE_HALF_HEIGHT = 0.4
const CAPSULE_RADIUS = 0.4
// GLB 실제 크기를 확인한 뒤 1.7m(170cm)에 맞게 조정
const MODEL_SCALE = 1

interface CharacterProps {
  targetPositionRef?: React.RefObject<THREE.Vector3>
  characterPositionRef?: React.RefObject<THREE.Vector3>
  isPointerDownRef?: React.RefObject<boolean>
}

export default function Character({
  targetPositionRef: externalTargetRef,
  characterPositionRef: externalCharacterRef,
  isPointerDownRef: externalPointerRef,
}: CharacterProps) {
  const rigidBodyRef = useRef<RapierRigidBody>(null)
  const groupRef = useRef<THREE.Group>(null)

  // props가 없을 때 fallback refs (HubScene 등 독립 사용 시)
  const internalTargetRef = useRef<THREE.Vector3>(new THREE.Vector3())
  const internalCharacterRef = useRef<THREE.Vector3>(new THREE.Vector3())
  const internalPointerRef = useRef<boolean>(false)

  const targetPositionRef = externalTargetRef ?? internalTargetRef
  const characterPositionRef = externalCharacterRef ?? internalCharacterRef
  const isPointerDownRef = externalPointerRef ?? internalPointerRef

  const { scene, animations } = useGLTF(MODEL_PATH)
  const { actions, names } = useAnimations(animations, groupRef)

  const isMovingRef = useCharacterMovement(
    rigidBodyRef,
    targetPositionRef,
    isPointerDownRef,
    characterPositionRef,
  )

  // 마운트 시 idle 애니메이션 재생
  useEffect(() => {
    const idleName = names.find((name) => /idle/i.test(name)) ?? names[0]
    if (idleName) {
      actions[idleName]?.reset().play()
    }
  }, [actions, names])

  // 이동 상태 감지 → 애니메이션 전환 (useFrame 안에서 ref 비교)
  const prevIsMovingRef = useRef(false)
  useFrame(() => {
    const isNowMoving = isMovingRef.current
    if (isNowMoving === prevIsMovingRef.current) return
    prevIsMovingRef.current = isNowMoving

    const idleName = names.find((name) => /idle/i.test(name)) ?? names[0]
    const walkName = names.find((name) => /walk/i.test(name)) ?? names[1]

    if (isNowMoving) {
      if (idleName) actions[idleName]?.fadeOut(0.2)
      if (walkName) actions[walkName]?.reset().fadeIn(0.2).play()
    } else {
      if (walkName) actions[walkName]?.fadeOut(0.2)
      if (idleName) actions[idleName]?.reset().fadeIn(0.2).play()
    }
  })

  return (
    <RigidBody
      ref={rigidBodyRef}
      type="kinematicPosition"
      colliders={false}
      position={[0, CAPSULE_HALF_HEIGHT + CAPSULE_RADIUS, 0]}
    >
      <CapsuleCollider args={[CAPSULE_HALF_HEIGHT, CAPSULE_RADIUS]} />
      <group ref={groupRef} scale={MODEL_SCALE}>
        <primitive object={scene} />
      </group>
    </RigidBody>
  )
}

useGLTF.preload(MODEL_PATH)
