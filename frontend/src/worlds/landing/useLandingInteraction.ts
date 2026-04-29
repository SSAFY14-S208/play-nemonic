import { useEffect, useRef } from 'react'
import { useThree } from '@react-three/fiber'
import * as THREE from 'three'

const GROUND_PLANE = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0)

export function useLandingInteraction(
  targetPositionRef: React.RefObject<THREE.Vector3>,
  isPointerDownRef: React.RefObject<boolean>,
) {
  const { camera, gl } = useThree()
  const activePointerCountRef = useRef(0)

  useEffect(() => {
    const canvas = gl.domElement
    const raycaster = new THREE.Raycaster()
    const intersection = new THREE.Vector3()

    const updateTargetFromClient = (clientX: number, clientY: number) => {
      const rect = canvas.getBoundingClientRect()
      const ndcX = ((clientX - rect.left) / rect.width) * 2 - 1
      const ndcY = -((clientY - rect.top) / rect.height) * 2 + 1
      raycaster.setFromCamera(new THREE.Vector2(ndcX, ndcY), camera)
      const hit = raycaster.ray.intersectPlane(GROUND_PLANE, intersection)
      if (hit) {
        targetPositionRef.current.copy(intersection)
      }
    }

    const handlePointerDown = (event: PointerEvent) => {
      activePointerCountRef.current += 1
      // 멀티터치(핀치)일 때는 이동 비활성화
      if (activePointerCountRef.current > 1) {
        isPointerDownRef.current = false
        return
      }
      isPointerDownRef.current = true
      updateTargetFromClient(event.clientX, event.clientY)
    }

    const handlePointerMove = (event: PointerEvent) => {
      if (!isPointerDownRef.current) return
      updateTargetFromClient(event.clientX, event.clientY)
    }

    const handlePointerUp = () => {
      activePointerCountRef.current = Math.max(0, activePointerCountRef.current - 1)
      if (activePointerCountRef.current === 0) {
        isPointerDownRef.current = false
      }
    }

    canvas.addEventListener('pointerdown', handlePointerDown)
    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
    window.addEventListener('pointercancel', handlePointerUp)

    return () => {
      canvas.removeEventListener('pointerdown', handlePointerDown)
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
      window.removeEventListener('pointercancel', handlePointerUp)
    }
  }, [camera, gl, isPointerDownRef, targetPositionRef])
}
