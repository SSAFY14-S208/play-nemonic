'use client'

import { useCallback, useEffect, useState } from 'react'
import type { FlipbookStep } from '../constants'
import type { FlipbookFrame } from '../types'

export function useFlipbookResultPlayback({
  currentStep,
  frames,
}: {
  currentStep: FlipbookStep
  frames: FlipbookFrame[]
}) {
  const [resultFrameIndex, setResultFrameIndex] = useState(0)
  const [isGifPlaying, setIsGifPlaying] = useState(true)

  const activeResultFrame = frames[resultFrameIndex] ?? frames[0] ?? null
  const canGoPreviousResultFrame = resultFrameIndex > 0
  const canGoNextResultFrame = resultFrameIndex < frames.length - 1

  const resetResultFrameIndex = useCallback(() => {
    setResultFrameIndex(0)
  }, [])

  const showPreviousResultFrame = useCallback(() => {
    setResultFrameIndex((currentFrameIndex) => Math.max(0, currentFrameIndex - 1))
  }, [])

  const showNextResultFrame = useCallback(() => {
    setResultFrameIndex((currentFrameIndex) => Math.min(frames.length - 1, currentFrameIndex + 1))
  }, [frames.length])

  useEffect(() => {
    if (currentStep !== 'result' || !isGifPlaying || frames.length <= 1) return

    const timerId = window.setInterval(() => {
      setResultFrameIndex((currentFrameIndex) =>
        currentFrameIndex >= frames.length - 1 ? 0 : currentFrameIndex + 1,
      )
    }, 520)

    return () => window.clearInterval(timerId)
  }, [currentStep, frames.length, isGifPlaying])

  return {
    resultFrameIndex,
    activeResultFrame,
    isGifPlaying,
    canGoPreviousResultFrame,
    canGoNextResultFrame,
    resetResultFrameIndex,
    setIsGifPlaying,
    showPreviousResultFrame,
    showNextResultFrame,
  }
}
