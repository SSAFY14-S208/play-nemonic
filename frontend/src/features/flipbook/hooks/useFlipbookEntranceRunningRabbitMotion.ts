'use client'

import { useFrame } from '@react-three/fiber'
import type { RefObject } from 'react'
import type { Group } from 'three'

const RABBIT_CENTER_X = -0.26
const RABBIT_CENTER_Z = 0.05
const RABBIT_SURFACE_Y = 0.24
const RABBIT_ROTATION_Y = Math.PI / 2
const RABBIT_BOUNCE_HEIGHT = 0.038
const RABBIT_BOUNCE_SPEED = 9.5
const RABBIT_RUN_TILT_AMOUNT = 0.025

export function useFlipbookEntranceRunningRabbitMotion(
  rabbitGroupRef: RefObject<Group | null>,
) {
  useFrame(({ clock }) => {
    const rabbitGroup = rabbitGroupRef.current
    if (!rabbitGroup) return

    const runTime = clock.elapsedTime * RABBIT_BOUNCE_SPEED
    const bounce = Math.max(0, Math.sin(runTime)) * RABBIT_BOUNCE_HEIGHT

    rabbitGroup.position.set(RABBIT_CENTER_X, RABBIT_SURFACE_Y + bounce, RABBIT_CENTER_Z)
    rabbitGroup.rotation.y = RABBIT_ROTATION_Y
    rabbitGroup.rotation.z = Math.sin(runTime) * RABBIT_RUN_TILT_AMOUNT
  })
}
