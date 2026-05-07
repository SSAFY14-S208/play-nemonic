'use client'

import type { RefObject } from 'react'
import { useState } from 'react'
import { useMotionValueEvent, useScroll, useSpring, useTransform } from 'motion/react'

const FRAME_SEQUENCE_START_PROGRESS = 0.08
const FRAME_SEQUENCE_END_PROGRESS = 0.78
const RABBIT_VISUAL_MOUNT_PROGRESS = 0.8

export function useFlipbookEntranceTimeline(
  sectionRef: RefObject<HTMLElement | null>,
  frameCount: number,
) {
  const [activeFrameIndex, setActiveFrameIndex] = useState(0)
  const [shouldMountRabbitVisual, setShouldMountRabbitVisual] = useState(false)
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ['start start', 'end end'],
  })

  const smoothProgress = useSpring(scrollYProgress, {
    stiffness: 118,
    damping: 28,
    mass: 0.78,
  })

  useMotionValueEvent(smoothProgress, 'change', (latestProgress) => {
    if (!Number.isFinite(latestProgress) || frameCount <= 0) return

    const sequenceProgress =
      (latestProgress - FRAME_SEQUENCE_START_PROGRESS) /
      (FRAME_SEQUENCE_END_PROGRESS - FRAME_SEQUENCE_START_PROGRESS)
    const boundedSequenceProgress = Math.min(1, Math.max(0, sequenceProgress))
    const nextFrameIndex = Math.min(
      frameCount - 1,
      Math.floor(boundedSequenceProgress * frameCount),
    )

    setActiveFrameIndex((currentFrameIndex) =>
      currentFrameIndex === nextFrameIndex ? currentFrameIndex : nextFrameIndex,
    )
    setShouldMountRabbitVisual((currentShouldMountRabbitVisual) =>
      currentShouldMountRabbitVisual || latestProgress >= RABBIT_VISUAL_MOUNT_PROGRESS,
    )
  })

  const frameOpacity = useTransform(smoothProgress, [0, 0.05, 0.82, 0.95], [0, 1, 1, 0.26])
  const frameScale = useTransform(smoothProgress, [0, 0.18, 0.6, 0.8, 0.95], [0.38, 0.72, 1.04, 1.36, 1.74])
  const frameY = useTransform(smoothProgress, [0, 0.18, 0.72, 0.95], [96, 0, -20, -92])
  const frameRotate = useTransform(smoothProgress, [0, 0.18, 0.78, 0.95], [-3, 0, 0, 2])

  const actionOpacity = useTransform(smoothProgress, [0.82, 0.92], [0, 1])
  const actionY = useTransform(smoothProgress, [0.82, 0.94], [34, 0])

  return {
    activeFrameIndex,
    frameOpacity,
    frameScale,
    frameY,
    frameRotate,
    shouldMountRabbitVisual,
    actionOpacity,
    actionY,
  }
}
