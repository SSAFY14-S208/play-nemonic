import { useRef, Suspense } from 'react'
import * as THREE from 'three'
import Lighting from '../_infra/Lighting'
import Character from '../_infra/Character'
import GroundMesh from './objects/GroundMesh'
import LandingCamera from './LandingCamera'
import { useLandingInteraction } from './useLandingInteraction'

export default function LandingScene() {
  const targetPositionRef = useRef<THREE.Vector3>(new THREE.Vector3())
  const characterPositionRef = useRef<THREE.Vector3>(new THREE.Vector3(0, 0.85, 0))
  const isPointerDownRef = useRef<boolean>(false)

  useLandingInteraction(targetPositionRef, isPointerDownRef)

  return (
    <>
      <LandingCamera characterPositionRef={characterPositionRef} />
      <Lighting />
      <GroundMesh />
      <Suspense fallback={null}>
        <Character
          targetPositionRef={targetPositionRef}
          characterPositionRef={characterPositionRef}
          isPointerDownRef={isPointerDownRef}
        />
      </Suspense>
    </>
  )
}
