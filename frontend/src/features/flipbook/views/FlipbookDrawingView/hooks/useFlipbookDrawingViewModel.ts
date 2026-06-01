'use client'

import { useEffect, useState } from 'react'

import { useDrawingKeyboardShortcuts } from '@/shared/hooks'
import type { FlipbookConnectionStatus } from '@/shared/types'
import { useFlipbookBgm } from '@/features/flipbook/hooks'
import type { FlipbookDrawingSubmissionState } from '@/features/flipbook/types'

interface UseFlipbookDrawingViewModelParams {
  activeRoundIndex: number
  roundCount: number | null
  isSubmitting: boolean
  isRoundSubmitted: boolean
  isAssignmentReady: boolean
  connectionStatus: FlipbookConnectionStatus
  errorMessage: string | null
  previousFrameCount: number
  onUndoDrawing: () => void
  onRedoDrawing: () => void
  onCompleteRound: () => void | Promise<void>
}

export function useFlipbookDrawingViewModel({
  activeRoundIndex,
  roundCount,
  isSubmitting,
  isRoundSubmitted,
  isAssignmentReady,
  connectionStatus,
  errorMessage,
  previousFrameCount,
  onUndoDrawing,
  onRedoDrawing,
  onCompleteRound,
}: UseFlipbookDrawingViewModelParams) {
  const [isHowToPlayModalOpen, setIsHowToPlayModalOpen] = useState(false)
  const [submittedRoundIndex, setSubmittedRoundIndex] = useState<number | null>(null)
  const [isOnionSkinVisible, setIsOnionSkinVisible] = useState(true)
  const { audioRef, isBgmMuted, toggleFlipbookBgmMuted } = useFlipbookBgm({
    shouldStart: true,
  })

  const isConnectionUnstable =
    connectionStatus === 'reconnecting' || connectionStatus === 'disconnected'
  const displayRoundCount = Math.max(roundCount ?? activeRoundIndex + 1, activeRoundIndex + 1, 1)
  const isWaitingForNextRound =
    (isRoundSubmitted || submittedRoundIndex === activeRoundIndex) && !isSubmitting
  const drawingSubmissionState: FlipbookDrawingSubmissionState = isSubmitting
    ? 'submitting'
    : isWaitingForNextRound
      ? 'waiting'
      : 'drawing'
  const isDrawingLocked =
    !isAssignmentReady || isConnectionUnstable || drawingSubmissionState !== 'drawing'
  const hasOnionSkinHint = previousFrameCount > 0
  const instructionText =
    activeRoundIndex === 0
      ? '첫 장면을 그려주세요'
      : hasOnionSkinHint && isOnionSkinVisible
        ? '흐리게 보이는 이전 그림을 이어 그려주세요'
        : '다음 장면을 이어 그려주세요'
  const submitButtonText =
    drawingSubmissionState === 'submitting'
      ? '제출 중'
      : drawingSubmissionState === 'waiting'
        ? '대기 중'
        : '완료!'
  const overlayMessage =
    !isAssignmentReady
      ? '그림 종이를 준비하고 있어요'
      : drawingSubmissionState === 'submitting'
        ? '그림을 제출하고 있어요'
        : drawingSubmissionState === 'waiting'
          ? '제출 완료! 다음 라운드를 기다리는 중이에요'
          : null
  const hintToggleLabel = hasOnionSkinHint
    ? isOnionSkinVisible
      ? '힌트 끄기'
      : '힌트 보기'
    : '힌트 없음'

  const openHowToPlayModal = () => setIsHowToPlayModalOpen(true)

  const toggleOnionSkinVisibility = () => {
    if (!hasOnionSkinHint) return

    setIsOnionSkinVisible((currentVisibility) => !currentVisibility)
  }

  const handleCompleteRound = () => {
    if (isDrawingLocked) return

    setSubmittedRoundIndex(activeRoundIndex)
    void onCompleteRound()
  }

  useDrawingKeyboardShortcuts({
    enabled: !isDrawingLocked,
    onUndo: onUndoDrawing,
    onRedo: onRedoDrawing,
  })

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (submittedRoundIndex === null) return

      const shouldUnlockDrawing =
        (submittedRoundIndex !== activeRoundIndex && !isRoundSubmitted) || errorMessage !== null
      if (!cancelled && shouldUnlockDrawing) {
        setSubmittedRoundIndex(null)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeRoundIndex, errorMessage, isRoundSubmitted, submittedRoundIndex])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (!cancelled) {
        setIsOnionSkinVisible(true)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeRoundIndex, previousFrameCount])

  return {
    audioRef,
    displayRoundCount,
    hasOnionSkinHint,
    hintToggleLabel,
    instructionText,
    isBgmMuted,
    isConnectionUnstable,
    isDrawingLocked,
    isHowToPlayModalOpen,
    isOnionSkinVisible,
    overlayMessage,
    submitButtonText,
    handleCompleteRound,
    openHowToPlayModal,
    setIsHowToPlayModalOpen,
    toggleFlipbookBgmMuted,
    toggleOnionSkinVisibility,
  }
}

export type FlipbookDrawingViewModel = ReturnType<typeof useFlipbookDrawingViewModel>
