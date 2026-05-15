import { useCallback, useLayoutEffect, useRef } from 'react'
import { useFrame, useThree, type ThreeEvent } from '@react-three/fiber'
import * as THREE from 'three'

const START_BUTTON_HOVER_SCALE = 1.045
const START_BUTTON_HOVER_Y_OFFSET = 0.012
const START_BUTTON_PRESSED_SCALE = 0.95
const START_BUTTON_PRESSED_Y_OFFSET = -0.018
const START_BUTTON_BASE_GLOW_OPACITY = 0.08
const START_BUTTON_HOVER_GLOW_OPACITY = 0.38
const START_BUTTON_PRESSED_GLOW_OPACITY = 0.24
const START_BUTTON_ANIMATION_DAMPING = 14
const MONITOR_SCREEN_TRANSITION_OFFSET_Y = -0.004
const MONITOR_SCREEN_TRANSITION_SCALE = 0.998
const MONITOR_SCREEN_TRANSITION_DAMPING = 18
const MONITOR_LOGO_ENTRANCE_DELAY_SECONDS = 0.04
const MONITOR_BUTTON_ENTRANCE_DELAY_SECONDS = 0.2
const MONITOR_LAYER_ENTRANCE_DURATION_SECONDS = 0.32
const MONITOR_LOGO_ENTRANCE_Y_OFFSET = -0.035
const MONITOR_LOGO_ENTRANCE_FLOAT_AMOUNT = 0.012
const MONITOR_BUTTON_ENTRANCE_Y_OFFSET = -0.045
const MONITOR_LOGO_ENTRANCE_SCALE = 0.985
const MONITOR_BUTTON_ENTRANCE_SCALE = 0.96
const MONITOR_SEQUENCE_TOTAL_SECONDS =
  MONITOR_BUTTON_ENTRANCE_DELAY_SECONDS + MONITOR_LAYER_ENTRANCE_DURATION_SECONDS

type MonitorVector3 = [number, number, number]

export interface MonitorEntranceProgressRef {
  current: number
}

function setDocumentCursor(cursor: string) {
  if (typeof document === 'undefined') return

  document.body.style.cursor = cursor
}

function getEaseOutCubic(progress: number) {
  return 1 - Math.pow(1 - progress, 3)
}

function getDelayedEntranceProgress({
  delaySeconds,
  durationSeconds,
  elapsedSeconds,
}: {
  delaySeconds: number
  durationSeconds: number
  elapsedSeconds: number
}) {
  const rawProgress = THREE.MathUtils.clamp(
    (elapsedSeconds - delaySeconds) / durationSeconds,
    0,
    1,
  )

  return getEaseOutCubic(rawProgress)
}

export function useMonitorEntranceSequence({
  selectedGameIndex,
}: {
  selectedGameIndex: number
}) {
  const screenGroupRef = useRef<THREE.Group>(null)
  const buttonEntranceProgressRef = useRef(1)
  const logoEntranceProgressRef = useRef(1)
  const previousGameIndexRef = useRef(selectedGameIndex)
  const sequenceElapsedSecondsRef = useRef(MONITOR_SEQUENCE_TOTAL_SECONDS)
  const transitionRef = useRef({
    offsetY: 0,
    scale: 1,
  })
  const invalidate = useThree((state) => state.invalidate)

  useLayoutEffect(() => {
    const previousGameIndex = previousGameIndexRef.current

    if (previousGameIndex === selectedGameIndex) return

    transitionRef.current.offsetY = MONITOR_SCREEN_TRANSITION_OFFSET_Y
    transitionRef.current.scale = MONITOR_SCREEN_TRANSITION_SCALE
    sequenceElapsedSecondsRef.current = 0
    logoEntranceProgressRef.current = 0
    buttonEntranceProgressRef.current = 0
    previousGameIndexRef.current = selectedGameIndex
    invalidate()
  }, [invalidate, selectedGameIndex])

  useFrame((_, delta) => {
    const screenGroup = screenGroupRef.current

    if (!screenGroup) return

    const transition = transitionRef.current
    sequenceElapsedSecondsRef.current = Math.min(
      sequenceElapsedSecondsRef.current + delta,
      MONITOR_SEQUENCE_TOTAL_SECONDS,
    )
    logoEntranceProgressRef.current = getDelayedEntranceProgress({
      delaySeconds: MONITOR_LOGO_ENTRANCE_DELAY_SECONDS,
      durationSeconds: MONITOR_LAYER_ENTRANCE_DURATION_SECONDS,
      elapsedSeconds: sequenceElapsedSecondsRef.current,
    })
    buttonEntranceProgressRef.current = getDelayedEntranceProgress({
      delaySeconds: MONITOR_BUTTON_ENTRANCE_DELAY_SECONDS,
      durationSeconds: MONITOR_LAYER_ENTRANCE_DURATION_SECONDS,
      elapsedSeconds: sequenceElapsedSecondsRef.current,
    })
    transition.offsetY = THREE.MathUtils.damp(
      transition.offsetY,
      0,
      MONITOR_SCREEN_TRANSITION_DAMPING,
      delta,
    )
    transition.scale = THREE.MathUtils.damp(
      transition.scale,
      1,
      MONITOR_SCREEN_TRANSITION_DAMPING,
      delta,
    )

    screenGroup.position.y = transition.offsetY
    screenGroup.scale.setScalar(transition.scale)

    if (
      Math.abs(transition.offsetY) > 0.001 ||
      Math.abs(transition.scale - 1) > 0.001 ||
      sequenceElapsedSecondsRef.current < MONITOR_SEQUENCE_TOTAL_SECONDS
    ) {
      invalidate()
    }
  })

  return {
    buttonEntranceProgressRef,
    logoEntranceProgressRef,
    screenGroupRef,
  }
}

export function useMonitorLayerEntranceAnimation({
  entranceProgressRef,
  entranceScale = MONITOR_LOGO_ENTRANCE_SCALE,
  entranceYOffset = MONITOR_LOGO_ENTRANCE_Y_OFFSET,
  position,
}: {
  entranceProgressRef?: MonitorEntranceProgressRef
  entranceScale?: number
  entranceYOffset?: number
  position: MonitorVector3
}) {
  const layerGroupRef = useRef<THREE.Group>(null)
  const materialRef = useRef<THREE.MeshBasicMaterial>(null)

  useFrame(() => {
    const layerGroup = layerGroupRef.current
    const material = materialRef.current

    if (!layerGroup || !material) return

    const entranceProgress = entranceProgressRef?.current ?? 1
    const entranceOffsetY = (1 - entranceProgress) * entranceYOffset
    const layerScale = THREE.MathUtils.lerp(
      entranceScale,
      1,
      entranceProgress,
    )

    layerGroup.position.set(position[0], position[1] + entranceOffsetY, position[2])
    layerGroup.scale.setScalar(layerScale)
    material.opacity = entranceProgress
  })

  return {
    layerGroupRef,
    materialRef,
  }
}

export function useMonitorLogoAnimation({
  entranceProgressRef,
  position,
}: {
  entranceProgressRef?: MonitorEntranceProgressRef
  position: MonitorVector3
}) {
  const logoGroupRef = useRef<THREE.Group>(null)
  const logoMaterialRef = useRef<THREE.MeshBasicMaterial>(null)

  useFrame(() => {
    const logoGroup = logoGroupRef.current
    const logoMaterial = logoMaterialRef.current

    if (!logoGroup || !logoMaterial) return

    const entranceProgress = entranceProgressRef?.current ?? 1
    const floatingYOffset =
      Math.sin(entranceProgress * Math.PI) * MONITOR_LOGO_ENTRANCE_FLOAT_AMOUNT
    const entranceYOffset =
      (1 - entranceProgress) * MONITOR_LOGO_ENTRANCE_Y_OFFSET
    const logoScale = THREE.MathUtils.lerp(
      MONITOR_LOGO_ENTRANCE_SCALE,
      1,
      entranceProgress,
    )

    logoGroup.position.set(
      position[0],
      position[1] + floatingYOffset + entranceYOffset,
      position[2],
    )
    logoGroup.scale.setScalar(logoScale)
    logoMaterial.opacity = entranceProgress
  })

  return {
    logoGroupRef,
    logoMaterialRef,
  }
}

export function useMonitorStartButtonAnimation({
  basePosition,
  entranceProgressRef,
  onClick,
}: {
  basePosition: MonitorVector3
  entranceProgressRef?: MonitorEntranceProgressRef
  onClick: () => void
}) {
  const buttonGroupRef = useRef<THREE.Group>(null)
  const buttonMaterialRef = useRef<THREE.MeshBasicMaterial>(null)
  const glowMaterialRef = useRef<THREE.MeshBasicMaterial>(null)
  const interactionRef = useRef({ hovered: false, pressed: false })
  const animationRef = useRef({
    glowOpacity: START_BUTTON_BASE_GLOW_OPACITY,
    scale: 1,
    yOffset: 0,
  })
  const invalidate = useThree((state) => state.invalidate)

  useFrame((_, delta) => {
    const buttonGroup = buttonGroupRef.current
    const buttonMaterial = buttonMaterialRef.current
    const glowMaterial = glowMaterialRef.current

    if (!buttonGroup || !buttonMaterial || !glowMaterial) return

    const entranceProgress = entranceProgressRef?.current ?? 1
    const { hovered, pressed } = interactionRef.current
    const targetScale = pressed
      ? START_BUTTON_PRESSED_SCALE
      : hovered
        ? START_BUTTON_HOVER_SCALE
        : 1
    const targetYOffset = pressed
      ? START_BUTTON_PRESSED_Y_OFFSET
      : hovered
        ? START_BUTTON_HOVER_Y_OFFSET
        : 0
    const targetGlowOpacity = pressed
      ? START_BUTTON_PRESSED_GLOW_OPACITY
      : hovered
        ? START_BUTTON_HOVER_GLOW_OPACITY
        : START_BUTTON_BASE_GLOW_OPACITY

    const animation = animationRef.current
    animation.scale = THREE.MathUtils.damp(
      animation.scale,
      targetScale,
      START_BUTTON_ANIMATION_DAMPING,
      delta,
    )
    animation.yOffset = THREE.MathUtils.damp(
      animation.yOffset,
      targetYOffset,
      START_BUTTON_ANIMATION_DAMPING,
      delta,
    )
    animation.glowOpacity = THREE.MathUtils.damp(
      animation.glowOpacity,
      targetGlowOpacity,
      START_BUTTON_ANIMATION_DAMPING,
      delta,
    )

    buttonGroup.position.set(
      basePosition[0],
      basePosition[1] +
        animation.yOffset +
        (1 - entranceProgress) * MONITOR_BUTTON_ENTRANCE_Y_OFFSET,
      basePosition[2],
    )
    buttonGroup.scale.setScalar(
      animation.scale *
        THREE.MathUtils.lerp(
          MONITOR_BUTTON_ENTRANCE_SCALE,
          1,
          entranceProgress,
        ),
    )
    buttonMaterial.opacity = entranceProgress
    glowMaterial.opacity = animation.glowOpacity * entranceProgress

    if (
      Math.abs(animation.scale - targetScale) > 0.001 ||
      Math.abs(animation.yOffset - targetYOffset) > 0.001 ||
      Math.abs(animation.glowOpacity - targetGlowOpacity) > 0.001
    ) {
      invalidate()
    }
  })

  const setInteractionState = useCallback(
    ({
      hovered,
      pressed,
    }: {
      hovered?: boolean
      pressed?: boolean
    }) => {
      if (hovered !== undefined) {
        interactionRef.current.hovered = hovered
      }

      if (pressed !== undefined) {
        interactionRef.current.pressed = pressed
      }

      invalidate()
    },
    [invalidate],
  )

  const handleButtonPointerEnter = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setDocumentCursor('pointer')
      setInteractionState({ hovered: true })
    },
    [setInteractionState],
  )

  const handleButtonPointerLeave = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setDocumentCursor('')
      setInteractionState({ hovered: false, pressed: false })
    },
    [setInteractionState],
  )

  const handleButtonPointerDown = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setInteractionState({ pressed: true })
    },
    [setInteractionState],
  )

  const handleButtonPointerUp = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setInteractionState({ pressed: false })
    },
    [setInteractionState],
  )

  const handleClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      setDocumentCursor('')
      onClick()
      invalidate()
    },
    [invalidate, onClick],
  )

  return {
    buttonGroupRef,
    buttonMaterialRef,
    glowMaterialRef,
    handleButtonPointerDown,
    handleButtonPointerEnter,
    handleButtonPointerLeave,
    handleButtonPointerUp,
    handleClick,
  }
}
