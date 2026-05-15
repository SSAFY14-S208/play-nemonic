'use client'

import { useCallback, useEffect, useState } from 'react'

interface UseFlipbookPrintRevealOptions {
  frameCount: number
  printDurationMs?: number
  holdDurationMs?: number
}

export function useFlipbookPrintReveal({
  frameCount,
  printDurationMs = 1700,
  holdDurationMs = 850,
}: UseFlipbookPrintRevealOptions) {
  const [activeFrameIndex, setActiveFrameIndex] = useState(0)
  const [revealedFrameCount, setRevealedFrameCount] = useState(0)
  const [isPlaying, setIsPlaying] = useState(frameCount > 0)
  const [printCycleKey, setPrintCycleKey] = useState(0)

  const hasFrames = frameCount > 0
  const isComplete = hasFrames && revealedFrameCount >= frameCount
  const canShowNext = hasFrames && activeFrameIndex < frameCount - 1

  const replay = useCallback(() => {
    setActiveFrameIndex(0)
    setRevealedFrameCount(frameCount > 0 ? 1 : 0)
    setIsPlaying(frameCount > 0)
    setPrintCycleKey((currentKey) => currentKey + 1)
  }, [frameCount])

  const showFrame = useCallback(
    (frameIndex: number) => {
      const clampedFrameIndex = Math.min(Math.max(0, frameIndex), Math.max(0, frameCount - 1))
      setActiveFrameIndex(clampedFrameIndex)
      setRevealedFrameCount(Math.max(clampedFrameIndex + 1, frameCount > 0 ? 1 : 0))
      setIsPlaying(false)
      setPrintCycleKey((currentKey) => currentKey + 1)
    },
    [frameCount],
  )

  const showNext = useCallback(() => {
    if (frameCount === 0) return

    const nextIndex = Math.min(activeFrameIndex + 1, frameCount - 1)
    setActiveFrameIndex(nextIndex)
    setRevealedFrameCount(Math.max(nextIndex + 1, 1))
    setPrintCycleKey((currentKey) => currentKey + 1)
  }, [activeFrameIndex, frameCount])

  const showAll = useCallback(() => {
    if (frameCount === 0) return

    setActiveFrameIndex(frameCount - 1)
    setRevealedFrameCount(frameCount)
    setIsPlaying(false)
    setPrintCycleKey((currentKey) => currentKey + 1)
  }, [frameCount])

  const pause = useCallback(() => {
    setIsPlaying(false)
  }, [])

  const play = useCallback(() => {
    if (frameCount === 0 || isComplete) return

    setIsPlaying(true)
  }, [frameCount, isComplete])

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (cancelled) return

      setActiveFrameIndex(0)
      setRevealedFrameCount(frameCount > 0 ? 1 : 0)
      setIsPlaying(frameCount > 0)
      setPrintCycleKey((currentKey) => currentKey + 1)
    })()

    return () => {
      cancelled = true
    }
  }, [frameCount])

  useEffect(() => {
    if (!isPlaying || !canShowNext) return

    const timerId = window.setTimeout(() => {
      showNext()
    }, printDurationMs + holdDurationMs)

    return () => {
      window.clearTimeout(timerId)
    }
  }, [canShowNext, holdDurationMs, isPlaying, printDurationMs, showNext])

  useEffect(() => {
    if (!isPlaying || canShowNext || !isComplete) return

    const timerId = window.setTimeout(() => {
      setIsPlaying(false)
    }, printDurationMs)

    return () => {
      window.clearTimeout(timerId)
    }
  }, [canShowNext, isComplete, isPlaying, printDurationMs])

  return {
    activeFrameIndex,
    revealedFrameCount,
    isPlaying,
    isComplete,
    canShowNext,
    printCycleKey,
    pause,
    play,
    replay,
    showAll,
    showFrame,
    showNext,
  }
}
