'use client'

import { useCallback, useEffect, useState } from 'react'
import type { FlipbookFrame, FlipbookStep } from '../types'

// 프레임당 표시 시간 (ms). useFlipbookResultAutoCycle도 이 값을 이용해 작품당
// 전체 재생 시간을 계산하므로, 상수화해서 단일 출처에서 관리한다.
export const FLIPBOOK_RESULT_FRAME_DURATION_MS = 520

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

  const showResultFrame = useCallback(
    (frameIndex: number) => {
      setResultFrameIndex(Math.min(Math.max(0, frameIndex), Math.max(0, frames.length - 1)))
    },
    [frames.length],
  )

  // 한 번 끝까지 재생한 뒤 마지막 프레임에서 정지(loop 안 함). useFlipbookResult
  // AutoCycle이 "다 재생 + 5초 홀드" 후 다음 작품으로 넘기는 흐름을 위해서다.
  // 루프를 유지하면 자동 전환 타이머가 발사되는 시점이 항상 mid-loop여서 결과가
  // 왔다갔다 보이는 문제가 있었음.
  //
  // 마지막 프레임 도달 시 isGifPlaying=false로 함께 끄면 다음 selectResult가
  // setIsGifPlaying(true)로 다시 시작시킬 수 있다.
  useEffect(() => {
    if (currentStep !== 'result' || !isGifPlaying || frames.length <= 1) return
    if (resultFrameIndex >= frames.length - 1) return

    const timeoutId = window.setTimeout(() => {
      const nextFrameIndex = resultFrameIndex + 1
      setResultFrameIndex(nextFrameIndex)
      if (nextFrameIndex >= frames.length - 1) {
        setIsGifPlaying(false)
      }
    }, FLIPBOOK_RESULT_FRAME_DURATION_MS)

    return () => window.clearTimeout(timeoutId)
  }, [currentStep, frames.length, isGifPlaying, resultFrameIndex])

  return {
    resultFrameIndex,
    activeResultFrame,
    isGifPlaying,
    canGoPreviousResultFrame,
    canGoNextResultFrame,
    resetResultFrameIndex,
    setIsGifPlaying,
    showResultFrame,
    showPreviousResultFrame,
    showNextResultFrame,
  }
}
