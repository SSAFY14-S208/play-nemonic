'use client'

import type { RefObject } from 'react'
import { useState } from 'react'
import {
  useMotionValueEvent,
  useReducedMotion,
  useScroll,
  useSpring,
  useTransform,
  type MotionStyle,
  type MotionValue,
} from 'motion/react'

const FRAME_SEQUENCE_START_PROGRESS = 0.08
const FRAME_SEQUENCE_END_PROGRESS = 0.78
const FINAL_STAGE_READY_PROGRESS = 0.86

type EntranceLayerKey =
  | 'roomBackdrop'
  | 'rightShelf'
  | 'cloudRug'
  | 'leftBooksProps'
  | 'starPillow'
  | 'floorCrayonsLeft'
  | 'floorCrayonsRight'
  | 'teddyBear'

type EntranceLayerStyleMap = Record<EntranceLayerKey, MotionStyle>

function useEntranceLayerStyle({
  progress,
  progressRange,
  fromX,
  fromY,
  fromScale = 1,
  fromOpacity = 0,
  reduceMotion,
}: {
  progress: MotionValue<number>
  progressRange: [number, number, number]
  fromX: number
  fromY: number
  fromScale?: number
  fromOpacity?: number
  reduceMotion: boolean
}): MotionStyle {
  return {
    opacity: useTransform(progress, progressRange, [fromOpacity, 0.78, 1]),
    x: useTransform(progress, progressRange, reduceMotion ? [0, 0, 0] : [fromX, fromX * 0.28, 0]),
    y: useTransform(progress, progressRange, reduceMotion ? [0, 0, 0] : [fromY, fromY * 0.28, 0]),
    scale: useTransform(progress, progressRange, reduceMotion ? [1, 1, 1] : [fromScale, 1, 1]),
  }
}

export function useFlipbookEntranceTimeline(
  sectionRef: RefObject<HTMLElement | null>,
  frameCount: number,
) {
  const [activeFrameIndex, setActiveFrameIndex] = useState(0)
  const [isFinalStageReady, setIsFinalStageReady] = useState(false)
  const shouldReduceMotion = useReducedMotion() ?? false
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
    setIsFinalStageReady((currentValue) => {
      const nextValue = latestProgress >= FINAL_STAGE_READY_PROGRESS

      return currentValue === nextValue ? currentValue : nextValue
    })
  })

  const frameOpacity = useTransform(smoothProgress, [0, 0.78, 0.9], [1, 1, 0])
  const frameScale = useTransform(smoothProgress, [0, 0.18, 0.6, 0.8, 1], [0.8, 0.82, 1.04, 1.24, 1.24])
  const frameY = useTransform(smoothProgress, [0, 0.72, 1], [0, -12, 0])
  const frameRotate = useTransform(smoothProgress, [0, 1], [0, 0])

  const actionOpacity = useTransform(smoothProgress, [0.82, 0.92], [0, 1])
  const actionY = useTransform(smoothProgress, [0.82, 0.94], [34, 0])
  const roomBaseOpacity = useTransform(smoothProgress, [0.16, 0.5, 1], [0, 0.9, 1])

  const starsOpacity = useTransform(smoothProgress, [0, 0.3, 1], [0.24, 0.34, 0.4])
  const starsX = useTransform(smoothProgress, [0, 1], [-10, 18])
  const starsY = useTransform(smoothProgress, [0, 1], [8, -14])

  const dotsOpacity = useTransform(smoothProgress, [0, 0.42, 1], [0.18, 0.26, 0.3])
  const dotsX = useTransform(smoothProgress, [0, 1], [16, -8])
  const dotsY = useTransform(smoothProgress, [0, 1], [-6, 10])

  const crayonOpacity = useTransform(smoothProgress, [0, 0.56, 1], [0.42, 0.48, 0.44])
  const crayonX = useTransform(smoothProgress, [0, 1], [-6, 8])
  const crayonY = useTransform(smoothProgress, [0, 1], [10, -8])

  const roomLayers: EntranceLayerStyleMap = {
    roomBackdrop: useEntranceLayerStyle({
      progress: smoothProgress,
      progressRange: [0.18, 0.58, 0.96],
      fromX: -18,
      fromY: 96,
      fromScale: 0.985,
      reduceMotion: shouldReduceMotion,
    }),
    rightShelf: useEntranceLayerStyle({
      progress: smoothProgress,
      progressRange: [0.3, 0.68, 0.98],
      fromX: 72,
      fromY: 138,
      fromScale: 0.975,
      reduceMotion: shouldReduceMotion,
    }),
    cloudRug: useEntranceLayerStyle({
      progress: smoothProgress,
      progressRange: [0.34, 0.72, 1],
      fromX: 10,
      fromY: 178,
      fromScale: 0.965,
      reduceMotion: shouldReduceMotion,
    }),
    leftBooksProps: useEntranceLayerStyle({
      progress: smoothProgress,
      progressRange: [0.38, 0.75, 1],
      fromX: -76,
      fromY: 144,
      fromScale: 0.98,
      reduceMotion: shouldReduceMotion,
    }),
    starPillow: useEntranceLayerStyle({
      progress: smoothProgress,
      progressRange: [0.42, 0.78, 1],
      fromX: -84,
      fromY: 170,
      fromScale: 0.95,
      reduceMotion: shouldReduceMotion,
    }),
    floorCrayonsLeft: useEntranceLayerStyle({
      progress: smoothProgress,
      progressRange: [0.48, 0.82, 1],
      fromX: -44,
      fromY: 210,
      fromScale: 0.97,
      reduceMotion: shouldReduceMotion,
    }),
    floorCrayonsRight: useEntranceLayerStyle({
      progress: smoothProgress,
      progressRange: [0.5, 0.84, 1],
      fromX: 46,
      fromY: 226,
      fromScale: 0.965,
      reduceMotion: shouldReduceMotion,
    }),
    teddyBear: useEntranceLayerStyle({
      progress: smoothProgress,
      progressRange: [0.56, 0.86, 1],
      fromX: 94,
      fromY: 200,
      fromScale: 0.955,
      reduceMotion: shouldReduceMotion,
    }),
  }

  return {
    activeFrameIndex,
    frameOpacity,
    frameScale,
    frameY,
    frameRotate,
    isFinalStageReady,
    actionOpacity,
    actionY,
    roomBaseOpacity,
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
    roomLayers,
  }
}
