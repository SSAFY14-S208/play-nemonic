import { useRef } from 'react'
import { useFrame } from '@react-three/fiber'
import { MathUtils, type Group, type Mesh } from 'three'
import { useHubViewStore } from '@/shared/stores'
import {
  HUB_WITCH_PLATFORM_POSITION,
  HUB_WITCH_PLATFORM_ROTATION_Y,
} from '../../constants'

const WITCH_HOVER_DAMPING = 8
const WITCH_IDLE_ROTATION_AMOUNT = 0.025
const WITCH_IDLE_ROTATION_SPEED = 0.9
const WITCH_HOVER_ROTATION_AMOUNT = 0.08
const WITCH_IDLE_FLOAT_AMOUNT = 0.025
const WITCH_IDLE_FLOAT_SPEED = 1.4
const WITCH_HOVER_FLOAT_AMOUNT = 0.035
const WITCH_SHADOW_BASE_SCALE_X = 1.35
const WITCH_SHADOW_BASE_SCALE_Y = 0.78
const WITCH_SHADOW_HOVER_SCALE_X = 0.14
const WITCH_SHADOW_HOVER_SCALE_Y = 0.07

export function useWitchHoverMotion(
  wrapperRef: React.RefObject<Group | null>,
  shadow: Mesh,
) {
  const hoverProgressRef = useRef(0)
  const isWitchHovered = useHubViewStore((state) => state.isWitchHovered)

  useFrame(({ clock }, delta) => {
    const wrapper = wrapperRef.current
    if (!wrapper) return

    const elapsedTime = clock.elapsedTime
    hoverProgressRef.current = MathUtils.damp(
      hoverProgressRef.current,
      isWitchHovered ? 1 : 0,
      WITCH_HOVER_DAMPING,
      delta,
    )
    const hoverProgress = hoverProgressRef.current

    wrapper.rotation.y = HUB_WITCH_PLATFORM_ROTATION_Y
      + Math.sin(elapsedTime * WITCH_IDLE_ROTATION_SPEED)
      * WITCH_IDLE_ROTATION_AMOUNT
      + hoverProgress * WITCH_HOVER_ROTATION_AMOUNT
    wrapper.position.y = HUB_WITCH_PLATFORM_POSITION[1]
      + Math.sin(elapsedTime * WITCH_IDLE_FLOAT_SPEED)
      * WITCH_IDLE_FLOAT_AMOUNT
      + hoverProgress * WITCH_HOVER_FLOAT_AMOUNT

    shadow.scale.set(
      WITCH_SHADOW_BASE_SCALE_X + hoverProgress * WITCH_SHADOW_HOVER_SCALE_X,
      WITCH_SHADOW_BASE_SCALE_Y + hoverProgress * WITCH_SHADOW_HOVER_SCALE_Y,
      1,
    )
  })
}
