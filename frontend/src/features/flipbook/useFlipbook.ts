'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useDrawingBoard } from '@/shared/hooks'
import type { DrawingLine } from '@/shared/types'
import {
  FLIPBOOK_BACKGROUND_COLOR,
  FLIPBOOK_BOARD_SIZE,
  FLIPBOOK_COLORS,
  FLIPBOOK_PARTICIPANTS,
  FLIPBOOK_TIME_LIMITS_SECONDS,
  getMinimumRoundCount,
  type FlipbookStep,
} from './constants'

export interface FlipbookFrame {
  id: string
  index: number
  drawnBy: string
  participantAvatar: string
  lines: DrawingLine[]
}

function compactFrames(frames: FlipbookFrame[]) {
  return frames
    .filter((frame) => frame.lines.length > 0)
    .map((frame, frameIndex) => ({ ...frame, index: frameIndex }))
}

export function useFlipbook() {
  const minimumRoundCount = getMinimumRoundCount(FLIPBOOK_PARTICIPANTS.length)
  const drawingBoard = useDrawingBoard({
    boardSize: FLIPBOOK_BOARD_SIZE,
    backgroundColor: FLIPBOOK_BACKGROUND_COLOR,
    defaultColor: FLIPBOOK_COLORS[0],
    defaultStrokeWidth: 6,
  })
  const [currentStep, setCurrentStep] = useState<FlipbookStep>('booth')
  const [selectedTimeLimitSeconds, setSelectedTimeLimitSeconds] = useState(
    FLIPBOOK_TIME_LIMITS_SECONDS[1],
  )
  const [roundCount, setRoundCount] = useState(Math.max(5, minimumRoundCount))
  const [activeRoundIndex, setActiveRoundIndex] = useState(0)
  const [remainingSeconds, setRemainingSeconds] = useState(selectedTimeLimitSeconds)
  const [frames, setFrames] = useState<FlipbookFrame[]>([])
  const [resultFrameIndex, setResultFrameIndex] = useState(0)
  const [isGifPlaying, setIsGifPlaying] = useState(true)

  const currentParticipant =
    FLIPBOOK_PARTICIPANTS[activeRoundIndex % FLIPBOOK_PARTICIPANTS.length]
  const compactedFrames = useMemo(() => compactFrames(frames), [frames])
  const previousFrameLines =
    activeRoundIndex > 0 && compactedFrames.length > 0
      ? compactedFrames[compactedFrames.length - 1].lines
      : []
  const progressText = `${Math.min(activeRoundIndex + 1, roundCount)}/${roundCount}`
  const canGoPreviousResultFrame = resultFrameIndex > 0
  const canGoNextResultFrame = resultFrameIndex < compactedFrames.length - 1
  const activeResultFrame = compactedFrames[resultFrameIndex] ?? compactedFrames[0] ?? null

  const selectStep = useCallback(
    (step: FlipbookStep) => {
      setCurrentStep(step)

      if (step === 'drawing') {
        setFrames([])
        setActiveRoundIndex(0)
        setRemainingSeconds(selectedTimeLimitSeconds)
        drawingBoard.replaceLines([])
      }

      if (step === 'result') {
        setResultFrameIndex(0)
      }
    },
    [drawingBoard, selectedTimeLimitSeconds],
  )

  const goToNextStep = useCallback(() => {
    if (currentStep === 'booth') {
      setCurrentStep('lobby')
      return
    }

    if (currentStep === 'lobby') {
      selectStep('drawing')
      return
    }

    if (currentStep === 'drawing') {
      setCurrentStep('result')
    }
  }, [currentStep, selectStep])

  const completeRound = useCallback(() => {
    const submittedLines = drawingBoard.lines
    const participant = FLIPBOOK_PARTICIPANTS[activeRoundIndex % FLIPBOOK_PARTICIPANTS.length]

    setFrames((currentFrames) => [
      ...currentFrames,
      {
        id: `frame-${activeRoundIndex + 1}`,
        index: currentFrames.length,
        drawnBy: participant.name.replace(' (나)', ''),
        participantAvatar: participant.avatar,
        lines: submittedLines,
      },
    ])

    drawingBoard.replaceLines([])

    if (activeRoundIndex >= roundCount - 1) {
      setCurrentStep('result')
      setResultFrameIndex(0)
      return
    }

    setActiveRoundIndex((currentRoundIndex) => currentRoundIndex + 1)
    setRemainingSeconds(selectedTimeLimitSeconds)
  }, [activeRoundIndex, drawingBoard, roundCount, selectedTimeLimitSeconds])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (currentStep !== 'drawing') return
      if (!cancelled) {
        setRemainingSeconds(selectedTimeLimitSeconds)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeRoundIndex, currentStep, selectedTimeLimitSeconds])

  useEffect(() => {
    if (currentStep !== 'drawing') return

    const timerId = window.setInterval(() => {
      setRemainingSeconds((currentSeconds) => Math.max(0, currentSeconds - 1))
    }, 1000)

    return () => window.clearInterval(timerId)
  }, [currentStep, activeRoundIndex])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (currentStep === 'drawing' && remainingSeconds === 0 && !cancelled) {
        completeRound()
      }
    })()

    return () => {
      cancelled = true
    }
  }, [completeRound, currentStep, remainingSeconds])

  useEffect(() => {
    if (currentStep !== 'result' || !isGifPlaying || compactedFrames.length <= 1) return

    const timerId = window.setInterval(() => {
      setResultFrameIndex((currentFrameIndex) =>
        currentFrameIndex >= compactedFrames.length - 1 ? 0 : currentFrameIndex + 1,
      )
    }, 520)

    return () => window.clearInterval(timerId)
  }, [compactedFrames.length, currentStep, isGifPlaying])

  const showPreviousResultFrame = useCallback(() => {
    setResultFrameIndex((currentFrameIndex) => Math.max(0, currentFrameIndex - 1))
  }, [])

  const showNextResultFrame = useCallback(() => {
    setResultFrameIndex((currentFrameIndex) =>
      Math.min(compactedFrames.length - 1, currentFrameIndex + 1),
    )
  }, [compactedFrames.length])

  const increaseRoundCount = useCallback(() => {
    setRoundCount((currentRoundCount) => currentRoundCount + 1)
  }, [])

  const decreaseRoundCount = useCallback(() => {
    setRoundCount((currentRoundCount) => Math.max(minimumRoundCount, currentRoundCount - 1))
  }, [minimumRoundCount])

  return {
    currentStep,
    selectedTimeLimitSeconds,
    roundCount,
    minimumRoundCount,
    activeRoundIndex,
    remainingSeconds,
    currentParticipant,
    previousFrameLines,
    progressText,
    frames: compactedFrames,
    resultFrameIndex,
    activeResultFrame,
    isGifPlaying,
    canGoPreviousResultFrame,
    canGoNextResultFrame,
    drawingBoard,
    selectStep,
    goToNextStep,
    completeRound,
    setSelectedTimeLimitSeconds,
    increaseRoundCount,
    decreaseRoundCount,
    setIsGifPlaying,
    showPreviousResultFrame,
    showNextResultFrame,
  }
}
