import { useEffect, useRef } from 'react'
import { useFrame, useThree } from '@react-three/fiber'
import type { Group } from 'three'
import { useHubViewStore } from '@/shared/stores'
import {
  HUB_CAMERA_HEIGHT,
  HUB_MODEL_ROOT_VERTICAL_OFFSET,
  HUB_NORMAL_ROTATION_EASE,
  HUB_NORMAL_ZOOM_EASE,
  HUB_VIEW_TRANSITION_ROTATION_EASE,
  HUB_VIEW_TRANSITION_ZOOM_EASE,
} from './constants'

const DRAG_ROTATION_FACTOR = 0.008
const AUTO_ROTATION_SPEED = 0.00115
const AUTO_ROTATION_DELAY_MS = 1800

function normalizeAngle(angle: number) {
  return Math.atan2(Math.sin(angle), Math.cos(angle))
}

export function useHubViewportControls(modelRootRef: React.RefObject<Group | null>) {
  const { camera, gl } = useThree()
  const currentRotationRef = useRef(useHubViewStore.getState().targetAngle)
  const targetRotationRef = useRef(useHubViewStore.getState().targetAngle)
  const currentZoomRef = useRef(useHubViewStore.getState().targetZoom)
  const targetZoomRef = useRef(useHubViewStore.getState().targetZoom)
  const pointerDownXRef = useRef(0)
  const pointerDownYRef = useRef(0)
  const lastPointerXRef = useRef(0)
  const isDraggingRef = useRef(false)

  useEffect(() => {
    const unsubscribe = useHubViewStore.subscribe((state, previousState) => {
      if (state.targetAngle !== previousState.targetAngle) {
        const shortestDelta = normalizeAngle(state.targetAngle - currentRotationRef.current)
        targetRotationRef.current = currentRotationRef.current + shortestDelta
      }
      if (state.targetZoom !== previousState.targetZoom) {
        targetZoomRef.current = state.targetZoom
      }
    })

    return unsubscribe
  }, [])

  useEffect(() => {
    const canvasElement = gl.domElement

    const handlePointerDown = (event: PointerEvent) => {
      isDraggingRef.current = true
      pointerDownXRef.current = event.clientX
      pointerDownYRef.current = event.clientY
      lastPointerXRef.current = event.clientX
      targetRotationRef.current = currentRotationRef.current
      useHubViewStore.getState().setTargetAngle(currentRotationRef.current)
      useHubViewStore.getState().setDragging(true)
      useHubViewStore.getState().cancelViewTransition()
      useHubViewStore.getState().markInteraction()
    }

    const handlePointerMove = (event: PointerEvent) => {
      if (!isDraggingRef.current) return

      const dragDistance = Math.hypot(
        event.clientX - pointerDownXRef.current,
        event.clientY - pointerDownYRef.current,
      )
      const pointerDeltaX = event.clientX - lastPointerXRef.current
      lastPointerXRef.current = event.clientX

      if (dragDistance > 2) {
        targetRotationRef.current += pointerDeltaX * DRAG_ROTATION_FACTOR
        useHubViewStore.getState().adjustTargetAngle(pointerDeltaX * DRAG_ROTATION_FACTOR)
      }
    }

    const handlePointerUp = () => {
      if (!isDraggingRef.current) return
      isDraggingRef.current = false
      useHubViewStore.getState().setDragging(false)
    }

    const handleWheel = (event: WheelEvent) => {
      event.preventDefault()
      const nextZoom = targetZoomRef.current + event.deltaY * 0.008
      useHubViewStore.getState().setTargetZoom(nextZoom)
    }

    canvasElement.addEventListener('pointerdown', handlePointerDown)
    canvasElement.addEventListener('wheel', handleWheel, { passive: false })
    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
    window.addEventListener('pointerleave', handlePointerUp)

    return () => {
      canvasElement.removeEventListener('pointerdown', handlePointerDown)
      canvasElement.removeEventListener('wheel', handleWheel)
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
      window.removeEventListener('pointerleave', handlePointerUp)
    }
  }, [gl])

  useFrame(() => {
    const now = typeof performance === 'undefined' ? Date.now() : performance.now()
    const {
      isWitchHovered,
      viewTransitionUntil,
      lastInteractionAt,
      markInteraction,
    } = useHubViewStore.getState()
    const isViewTransitioning = now < viewTransitionUntil
    const isWitchRotationPaused = isWitchHovered

    if (isWitchRotationPaused) {
      targetRotationRef.current = currentRotationRef.current
      markInteraction()
    } else if (
      !isDraggingRef.current
      && !isViewTransitioning
      && now - lastInteractionAt > AUTO_ROTATION_DELAY_MS
    ) {
      targetRotationRef.current += AUTO_ROTATION_SPEED
    }

    const rotationEase = isViewTransitioning
      ? HUB_VIEW_TRANSITION_ROTATION_EASE
      : HUB_NORMAL_ROTATION_EASE
    const zoomEase = isViewTransitioning
      ? HUB_VIEW_TRANSITION_ZOOM_EASE
      : HUB_NORMAL_ZOOM_EASE

    currentRotationRef.current += (
      targetRotationRef.current - currentRotationRef.current
    ) * rotationEase
    currentZoomRef.current += (targetZoomRef.current - currentZoomRef.current) * zoomEase

    camera.position.set(0, HUB_CAMERA_HEIGHT, currentZoomRef.current)
    camera.lookAt(0, 0.05, 0)

    const modelRoot = modelRootRef.current
    if (!modelRoot) return

    modelRoot.rotation.set(0, currentRotationRef.current, 0)
    modelRoot.position.y = HUB_MODEL_ROOT_VERTICAL_OFFSET
  })
}
