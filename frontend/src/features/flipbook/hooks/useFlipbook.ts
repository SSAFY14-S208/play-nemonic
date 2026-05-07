'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useDrawingBoard } from '@/shared/hooks'
import { useUserStore } from '@/shared/stores'
import { createRasterizedDrawingLine } from '@/shared/utils'
import {
  FLIPBOOK_BACKGROUND_COLOR,
  FLIPBOOK_BOARD_SIZE,
  FLIPBOOK_COLORS,
  type FlipbookStep,
} from '../constants'
import type { FlipbookFrame } from '../types'
import { createLocalFlipbookParticipant } from '../utils'
import { useFlipbookRealtimeActions } from './useFlipbookRealtimeActions'
import { useFlipbookRealtimeConnection } from './useFlipbookRealtimeConnection'
import { useFlipbookResultPlayback } from './useFlipbookResultPlayback'
import { useFlipbookSessionModel } from './useFlipbookSessionModel'
import { useFlipbookSettings } from './useFlipbookSettings'
import { useFlipbookTimer } from './useFlipbookTimer'

export function useFlipbook() {
  const userUuid = useUserStore((state) => state.userUuid)
  const nickname = useUserStore((state) => state.nickname)
  const realtimeActions = useFlipbookRealtimeActions()
  const currentParticipant = useMemo(
    () => createLocalFlipbookParticipant({ nickname, userUuid }),
    [nickname, userUuid],
  )
  const sessionParticipants = useMemo(() => [currentParticipant], [currentParticipant])
  const participantCount = 1
  const minimumRoundCount = 1
  const drawingBoard = useDrawingBoard({
    boardSize: FLIPBOOK_BOARD_SIZE,
    backgroundColor: FLIPBOOK_BACKGROUND_COLOR,
    defaultColor: FLIPBOOK_COLORS[0],
    defaultStrokeWidth: 6,
  })
  const [currentStep, setCurrentStep] = useState<FlipbookStep>('booth')
  const [activeRoundIndex, setActiveRoundIndex] = useState(0)
  const [frames, setFrames] = useState<FlipbookFrame[]>([])
  const isCompletingRoundRef = useRef(false)
  useFlipbookRealtimeConnection({ enabled: currentStep !== 'booth' })
  const flipbookSettings = useFlipbookSettings({
    minimumRoundCount,
    participantCount,
    realtimeActions,
  })
  const sessionModel = useFlipbookSessionModel({
    activeRoundIndex,
    currentFrameLines: drawingBoard.lines,
    currentParticipantUserUuid: currentParticipant.userUuid,
    currentStep,
    frames,
    roomId: realtimeActions.roomId,
    roundCount: flipbookSettings.roundCount,
    settings: flipbookSettings.settings,
    participants: sessionParticipants,
  })
  const resultPlayback = useFlipbookResultPlayback({
    currentStep,
    frames: sessionModel.compactedFrames,
  })

  const resetDrawingSession = useCallback(() => {
    setFrames([])
    setActiveRoundIndex(0)
    drawingBoard.replaceLines([])
  }, [drawingBoard])

  const selectStep = useCallback(
    (step: FlipbookStep) => {
      setCurrentStep(step)

      if (step === 'drawing') {
        resetDrawingSession()
      }

      if (step === 'result') {
        resultPlayback.resetResultFrameIndex()
      }
    },
    [resetDrawingSession, resultPlayback],
  )

  const createRoom = useCallback(() => {
    realtimeActions.enqueueCreateRoom()
    setCurrentStep('lobby')
  }, [realtimeActions])

  const enterRoom = useCallback(() => {
    realtimeActions.enqueueEnterRoom()
    setCurrentStep('lobby')
  }, [realtimeActions])

  const startGame = useCallback(() => {
    realtimeActions.enqueueStartGame(flipbookSettings.settings)
    selectStep('drawing')
  }, [flipbookSettings.settings, realtimeActions, selectStep])

  const completeRound = useCallback(async () => {
    if (isCompletingRoundRef.current) return

    isCompletingRoundRef.current = true

    try {
      const submittedLines = drawingBoard.lines
      const frameId = `frame-${activeRoundIndex + 1}`
      const rasterizedFrameLine = await createRasterizedDrawingLine({
        backgroundColor: FLIPBOOK_BACKGROUND_COLOR,
        boardSize: FLIPBOOK_BOARD_SIZE,
        id: `${frameId}-rasterized`,
        lines: submittedLines,
      })
      const frameLines = rasterizedFrameLine ? [rasterizedFrameLine] : []

      realtimeActions.enqueueFrameSubmit({
        assignment: sessionModel.activeAssignment,
        lines: frameLines,
      })

      setFrames((currentFrames) => [
        ...currentFrames,
        {
          id: frameId,
          index: currentFrames.length,
          drawnByUserUuid: currentParticipant.userUuid,
          drawnBy: currentParticipant.name.replace(' (나)', ''),
          participantAvatar: currentParticipant.avatar,
          lines: frameLines,
        },
      ])

      drawingBoard.replaceLines([])

      if (activeRoundIndex >= flipbookSettings.roundCount - 1) {
        setCurrentStep('result')
        resultPlayback.resetResultFrameIndex()
        return
      }

      setActiveRoundIndex((currentRoundIndex) => currentRoundIndex + 1)
    } finally {
      isCompletingRoundRef.current = false
    }
  }, [
    activeRoundIndex,
    currentParticipant,
    drawingBoard,
    flipbookSettings.roundCount,
    realtimeActions,
    resultPlayback,
    sessionModel.activeAssignment,
  ])

  const timer = useFlipbookTimer({
    activeRoundIndex,
    currentStep,
    selectedTimeLimitSeconds: flipbookSettings.selectedTimeLimitSeconds,
    onTimeExpired: completeRound,
  })

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (!cancelled) {
        realtimeActions.syncSessionSnapshot(sessionModel.sessionSnapshot)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [realtimeActions, sessionModel.sessionSnapshot])

  const leaveRoom = useCallback(() => {
    realtimeActions.enqueueLeaveRoom()
    setCurrentStep('lobby')
  }, [realtimeActions])

  return {
    currentStep,
    selectedTimeLimitSeconds: flipbookSettings.selectedTimeLimitSeconds,
    roundCount: flipbookSettings.roundCount,
    minimumRoundCount,
    activeRoundIndex,
    remainingSeconds: timer.remainingSeconds,
    currentParticipant,
    previousFrameLines: sessionModel.previousFrameLines,
    progressText: sessionModel.progressText,
    frames: sessionModel.compactedFrames,
    resultFrameIndex: resultPlayback.resultFrameIndex,
    activeResultFrame: resultPlayback.activeResultFrame,
    isGifPlaying: resultPlayback.isGifPlaying,
    canGoPreviousResultFrame: resultPlayback.canGoPreviousResultFrame,
    canGoNextResultFrame: resultPlayback.canGoNextResultFrame,
    connectionStatus: realtimeActions.connectionStatus,
    outboundMessages: realtimeActions.outboundMessages,
    sessionSnapshot: sessionModel.sessionSnapshot,
    drawingBoard,
    createRoom,
    enterRoom,
    selectStep,
    startGame,
    completeRound,
    selectTimeLimit: flipbookSettings.selectTimeLimit,
    increaseRoundCount: flipbookSettings.increaseRoundCount,
    decreaseRoundCount: flipbookSettings.decreaseRoundCount,
    leaveRoom,
    setIsGifPlaying: resultPlayback.setIsGifPlaying,
    showPreviousResultFrame: resultPlayback.showPreviousResultFrame,
    showNextResultFrame: resultPlayback.showNextResultFrame,
  }
}
