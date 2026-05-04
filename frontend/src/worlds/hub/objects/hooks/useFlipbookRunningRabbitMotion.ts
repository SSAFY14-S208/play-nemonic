import { useFrame } from '@react-three/fiber'
import type { Group } from 'three'
import {
  HUB_FLIPBOOK_RUNNING_RABBIT_CENTER_X,
  HUB_FLIPBOOK_RUNNING_RABBIT_CENTER_Z,
  HUB_FLIPBOOK_RUNNING_RABBIT_ROTATION_Y,
  HUB_FLIPBOOK_RUNNING_RABBIT_SURFACE_Y,
} from '../../constants'

const RABBIT_BOUNCE_HEIGHT = 0.045
const RABBIT_BOUNCE_SPEED = 9.5
const RABBIT_RUN_TILT_AMOUNT = 0.025

export function useFlipbookRunningRabbitMotion(
  rabbitGroupRef: React.RefObject<Group | null>,
) {
  useFrame(({ clock }) => {
    const rabbitGroup = rabbitGroupRef.current
    if (!rabbitGroup) return

    const runTime = clock.elapsedTime * RABBIT_BOUNCE_SPEED
    const bounce = Math.max(0, Math.sin(runTime))
      * RABBIT_BOUNCE_HEIGHT

    rabbitGroup.position.set(
      HUB_FLIPBOOK_RUNNING_RABBIT_CENTER_X,
      HUB_FLIPBOOK_RUNNING_RABBIT_SURFACE_Y + bounce,
      HUB_FLIPBOOK_RUNNING_RABBIT_CENTER_Z,
    )
    rabbitGroup.rotation.y = HUB_FLIPBOOK_RUNNING_RABBIT_ROTATION_Y
    rabbitGroup.rotation.z = Math.sin(runTime) * RABBIT_RUN_TILT_AMOUNT
  })
}
