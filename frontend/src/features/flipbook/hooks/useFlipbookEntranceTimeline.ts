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

  const frameOpacity = useTransform(smoothProgress, [0, 0.82, 0.95], [1, 1, 0.26])
  const frameScale = useTransform(smoothProgress, [0, 0.18, 0.6, 0.8, 0.95], [0.8, 0.82, 1.04, 1.36, 1.74])
  const frameY = useTransform(smoothProgress, [0, 0.72, 0.95], [0, -20, -92])
  const frameRotate = useTransform(smoothProgress, [0, 0.78, 0.95], [0, 0, 2])

  const actionOpacity = useTransform(smoothProgress, [0.82, 0.92], [0, 1])
  const actionY = useTransform(smoothProgress, [0.82, 0.94], [34, 0])

  const starsOpacity = useTransform(smoothProgress, [0, 0.3, 1], [0.24, 0.34, 0.4])
  const starsX = useTransform(smoothProgress, [0, 1], [-10, 18])
  const starsY = useTransform(smoothProgress, [0, 1], [8, -14])

  const dotsOpacity = useTransform(smoothProgress, [0, 0.42, 1], [0.18, 0.26, 0.3])
  const dotsX = useTransform(smoothProgress, [0, 1], [16, -8])
  const dotsY = useTransform(smoothProgress, [0, 1], [-6, 10])

  const crayonOpacity = useTransform(smoothProgress, [0, 0.56, 1], [0.42, 0.48, 0.44])
  const crayonX = useTransform(smoothProgress, [0, 1], [-6, 8])
  const crayonY = useTransform(smoothProgress, [0, 1], [10, -8])

  return {
    activeFrameIndex,
    frameOpacity,
    frameScale,
    frameY,
    frameRotate,
    shouldMountRabbitVisual,
    actionOpacity,
    actionY,
    background: {
      stars: {
        opacity: starsOpacity,
        x: starsX,
        y: starsY,
      },
      dots: {
        opacity: dotsOpacity,
        x: dotsX,
        y: dotsY,
      },
      crayon: {
        opacity: crayonOpacity,
        x: crayonX,
        y: crayonY,
      },
    },
  }
}
