import { CameraControls } from '@react-three/drei'
import { useThree } from '@react-three/fiber'
import { useCallback, useEffect, useRef, type ElementRef } from 'react'
import {
  HUB_CAMERA_PRESETS,
  HUB_PERFORMANCE_PROFILES,
} from '@/shared/constants'
import { useHubRoomStore } from '@/shared/stores'
import type { HubPerformanceMode } from '@/shared/types'
import { trackHubControlEvent, trackHubInvalidate } from '@/shared/utils'

export default function CameraRig({
  performanceMode,
}: {
  performanceMode: HubPerformanceMode
}) {
  const controlsRef = useRef<ElementRef<typeof CameraControls>>(null)
  const hasAppliedInitialFocusRef = useRef(false)
  const focusKey = useHubRoomStore((state) => state.focusKey)
  const invalidate = useThree((state) => state.invalidate)
  const performanceProfile = HUB_PERFORMANCE_PROFILES[performanceMode]
  const trackControlFrame = useCallback(() => {
    trackHubControlEvent('control')
    trackHubInvalidate('camera.control')
  }, [])
  const trackRestFrame = useCallback(() => {
    trackHubControlEvent('rest')
    trackHubInvalidate('camera.rest')
  }, [])
  const trackSleepFrame = useCallback(() => {
    trackHubControlEvent('sleep')
    trackHubInvalidate('camera.sleep')
  }, [])

  useEffect(() => {
    const controls = controlsRef.current
    if (!controls) return

    const preset = HUB_CAMERA_PRESETS[focusKey]
    const shouldTransition =
      hasAppliedInitialFocusRef.current &&
      performanceProfile.smoothCameraTransitions

    hasAppliedInitialFocusRef.current = true
    void controls.setLookAt(
      preset.position[0],
      preset.position[1],
      preset.position[2],
      preset.target[0],
      preset.target[1],
      preset.target[2],
      shouldTransition,
    )
    trackHubInvalidate('camera.focus')
    invalidate()
  }, [focusKey, invalidate, performanceProfile.smoothCameraTransitions])

  return (
    <CameraControls
      ref={controlsRef}
      makeDefault
      azimuthRotateSpeed={0.55}
      draggingSmoothTime={performanceProfile.cameraDraggingSmoothTime}
      dollySpeed={0.52}
      maxAzimuthAngle={Math.PI * 0.42}
      maxDistance={12.8}
      maxPolarAngle={Math.PI * 0.47}
      minAzimuthAngle={-Math.PI * 0.72}
      minDistance={2.2}
      minPolarAngle={Math.PI * 0.17}
      polarRotateSpeed={0.42}
      onControl={trackControlFrame}
      onRest={trackRestFrame}
      onSleep={trackSleepFrame}
      smoothTime={performanceProfile.cameraSmoothTime}
      truckSpeed={0}
    />
  )
}
