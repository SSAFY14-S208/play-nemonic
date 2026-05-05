'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useDrawingBoard } from '@/shared/hooks'
import { useUserStore } from '@/shared/stores'
import type {
  DrawingLine,
  FlipbookClientMessage,
  FlipbookDrawingAssignment,
  FlipbookFramePayload,
  FlipbookSessionSettings,
  FlipbookSessionSnapshot,
} from '@/shared/types'
import {
  FLIPBOOK_BACKGROUND_COLOR,
  FLIPBOOK_BOARD_SIZE,
  FLIPBOOK_COLORS,
  FLIPBOOK_PARTICIPANTS,
  FLIPBOOK_ROOM_CODE,
  FLIPBOOK_TIME_LIMITS_SECONDS,
  FLIPBOOK_TOPIC,
  type FlipbookTimeLimitSeconds,
  getMinimumRoundCount,
  type FlipbookStep,
} from './constants'
import { useFlipbookRealtimeStore } from './flipbookRealtimeStore'

export interface FlipbookFrame {
  id: string
  index: number
  drawnByUserUuid: string
  drawnBy: string
  participantAvatar: string
  lines: DrawingLine[]
}

function compactFrames(frames: FlipbookFrame[]) {
  return frames
    .filter((frame) => frame.lines.length > 0)
    .map((frame, frameIndex) => ({ ...frame, index: frameIndex }))
}

function createRequestId(actionName: string) {
  return `${actionName}-${Date.now()}-${crypto.randomUUID()}`
}

function toFlipbookSettings({
  minimumRoundCount,
  participantCount,
  roundCount,
  selectedTimeLimitSeconds,
}: {
  minimumRoundCount: number
  participantCount: number
  roundCount: number
  selectedTimeLimitSeconds: FlipbookTimeLimitSeconds
}): FlipbookSessionSettings {
  return {
    timeLimitSeconds: selectedTimeLimitSeconds,
    roundCount,
    minimumRoundCount,
    frameCountPerFlipbook: participantCount * roundCount,
  }
}

function toFramePayloads(frames: FlipbookFrame[]): FlipbookFramePayload[] {
  return frames.map((frame) => ({
    frameId: frame.id,
    index: frame.index,
    flipbookId: 'demo-flipbook',
    drawnByUserUuid: frame.drawnByUserUuid,
    drawnByNickname: frame.drawnBy,
    lines: frame.lines,
    isEmpty: frame.lines.length === 0,
  }))
}

export function useFlipbook() {
  const userUuid = useUserStore((state) => state.userUuid)
  const nickname = useUserStore((state) => state.nickname)
  const connectionStatus = useFlipbookRealtimeStore((state) => state.connectionStatus)
  const roomId = useFlipbookRealtimeStore((state) => state.roomId)
  const outboundMessages = useFlipbookRealtimeStore((state) => state.outboundMessages)
  const enqueueRealtimeClientMessage = useFlipbookRealtimeStore(
    (state) => state.enqueueClientMessage,
  )
  const setRealtimeSessionSnapshot = useFlipbookRealtimeStore(
    (state) => state.setSessionSnapshot,
  )
  const minimumRoundCount = getMinimumRoundCount(FLIPBOOK_PARTICIPANTS.length)
  const drawingBoard = useDrawingBoard({
    boardSize: FLIPBOOK_BOARD_SIZE,
    backgroundColor: FLIPBOOK_BACKGROUND_COLOR,
    defaultColor: FLIPBOOK_COLORS[0],
    defaultStrokeWidth: 6,
  })
  const [currentStep, setCurrentStep] = useState<FlipbookStep>('booth')
  const [selectedTimeLimitSeconds, setSelectedTimeLimitSeconds] = useState<
    FlipbookTimeLimitSeconds
  >(FLIPBOOK_TIME_LIMITS_SECONDS[1])
  const [roundCount, setRoundCount] = useState(Math.max(5, minimumRoundCount))
  const [activeRoundIndex, setActiveRoundIndex] = useState(0)
  const [remainingSeconds, setRemainingSeconds] = useState<number>(selectedTimeLimitSeconds)
  const [frames, setFrames] = useState<FlipbookFrame[]>([])
  const [resultFrameIndex, setResultFrameIndex] = useState(0)
  const [isGifPlaying, setIsGifPlaying] = useState(true)

  const currentParticipant =
    FLIPBOOK_PARTICIPANTS[activeRoundIndex % FLIPBOOK_PARTICIPANTS.length]
  const compactedFrames = useMemo(() => compactFrames(frames), [frames])
  const previousFrameLines = useMemo(
    () =>
      activeRoundIndex > 0 && compactedFrames.length > 0
        ? compactedFrames[compactedFrames.length - 1].lines
        : [],
    [activeRoundIndex, compactedFrames],
  )
  const progressText = `${Math.min(activeRoundIndex + 1, roundCount)}/${roundCount}`
  const canGoPreviousResultFrame = resultFrameIndex > 0
  const canGoNextResultFrame = resultFrameIndex < compactedFrames.length - 1
  const activeResultFrame = compactedFrames[resultFrameIndex] ?? compactedFrames[0] ?? null
  const settings = useMemo(
    () =>
      toFlipbookSettings({
        minimumRoundCount,
        participantCount: FLIPBOOK_PARTICIPANTS.length,
        roundCount,
        selectedTimeLimitSeconds,
      }),
    [minimumRoundCount, roundCount, selectedTimeLimitSeconds],
  )
  const activeAssignment = useMemo<FlipbookDrawingAssignment | null>(() => {
    if (currentStep !== 'drawing') return null

    return {
      roundIndex: activeRoundIndex,
      frameId: `frame-${activeRoundIndex + 1}`,
      flipbookId: 'demo-flipbook',
      drawingUserUuid: currentParticipant.userUuid,
      onionSkinFrameId: previousFrameLines.length > 0 ? `frame-${activeRoundIndex}` : null,
      onionSkinLines: previousFrameLines,
      deadlineAt: null,
    }
  }, [activeRoundIndex, currentParticipant.userUuid, currentStep, previousFrameLines])
  const completedFramePayloads = useMemo(() => toFramePayloads(compactedFrames), [compactedFrames])
  const sessionSnapshot = useMemo<FlipbookSessionSnapshot>(
    () => ({
      roomId,
      roomCode: FLIPBOOK_ROOM_CODE,
      topic: FLIPBOOK_TOPIC,
      phase: currentStep,
      participants: FLIPBOOK_PARTICIPANTS.map((participant, participantIndex) => ({
        userUuid: participant.userUuid,
        nickname: participant.name,
        avatar: participant.avatar,
        joinedOrder: participantIndex,
        isHost: participant.isHost === true,
        connectionStatus: 'online',
      })),
      settings,
      activeAssignment,
      currentFrameLines: drawingBoard.lines,
      completedFrames: completedFramePayloads,
      result:
        currentStep === 'result'
          ? {
              flipbookId: 'demo-flipbook',
              frames: completedFramePayloads,
              gifUrl: '/api/mock/flipbook/flipbook_uuid.gif',
            }
          : null,
      serverSyncedAt: null,
    }),
    [
      activeAssignment,
      completedFramePayloads,
      currentStep,
      drawingBoard.lines,
      roomId,
      settings,
    ],
  )

  const enqueueClientMessage = useCallback(
    (message: FlipbookClientMessage) => {
      enqueueRealtimeClientMessage(message)
    },
    [enqueueRealtimeClientMessage],
  )

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (!cancelled) {
        setRealtimeSessionSnapshot(sessionSnapshot)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [sessionSnapshot, setRealtimeSessionSnapshot])

  const createRoom = useCallback(() => {
    enqueueClientMessage({
      type: 'flipbook.room.create',
      requestId: createRequestId('room-create'),
      payload: {
        userUuid,
        nickname: nickname ?? '여우',
      },
    })
    setCurrentStep('lobby')
  }, [enqueueClientMessage, nickname, userUuid])

  const enterRoom = useCallback(() => {
    enqueueClientMessage({
      type: 'flipbook.room.join',
      requestId: createRequestId('room-join'),
      payload: {
        roomId,
        roomCode: FLIPBOOK_ROOM_CODE,
        userUuid,
        nickname: nickname ?? '여우',
      },
    })
    setCurrentStep('lobby')
  }, [enqueueClientMessage, nickname, roomId, userUuid])

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

  const startGame = useCallback(() => {
    enqueueClientMessage({
      type: 'flipbook.game.start',
      requestId: createRequestId('game-start'),
      payload: {
        roomId,
        settings,
      },
    })
    selectStep('drawing')
  }, [enqueueClientMessage, roomId, selectStep, settings])

  const completeRound = useCallback(() => {
    const submittedLines = drawingBoard.lines
    const participant = FLIPBOOK_PARTICIPANTS[activeRoundIndex % FLIPBOOK_PARTICIPANTS.length]

    enqueueClientMessage({
      type: 'flipbook.frame.submit',
      requestId: createRequestId('frame-submit'),
      payload: {
        roomId,
        assignment: activeAssignment,
        lines: submittedLines,
        submittedAt: new Date().toISOString(),
      },
    })

    setFrames((currentFrames) => [
      ...currentFrames,
      {
        id: `frame-${activeRoundIndex + 1}`,
        index: currentFrames.length,
        drawnByUserUuid: participant.userUuid,
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
  }, [
    activeAssignment,
    activeRoundIndex,
    drawingBoard,
    enqueueClientMessage,
    roomId,
    roundCount,
    selectedTimeLimitSeconds,
  ])

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
    setRoundCount((currentRoundCount) => {
      const nextRoundCount = currentRoundCount + 1
      enqueueClientMessage({
        type: 'flipbook.settings.update',
        requestId: createRequestId('settings-update'),
        payload: toFlipbookSettings({
          minimumRoundCount,
          participantCount: FLIPBOOK_PARTICIPANTS.length,
          roundCount: nextRoundCount,
          selectedTimeLimitSeconds,
        }),
      })
      return nextRoundCount
    })
  }, [enqueueClientMessage, minimumRoundCount, selectedTimeLimitSeconds])

  const decreaseRoundCount = useCallback(() => {
    setRoundCount((currentRoundCount) => {
      const nextRoundCount = Math.max(minimumRoundCount, currentRoundCount - 1)
      enqueueClientMessage({
        type: 'flipbook.settings.update',
        requestId: createRequestId('settings-update'),
        payload: toFlipbookSettings({
          minimumRoundCount,
          participantCount: FLIPBOOK_PARTICIPANTS.length,
          roundCount: nextRoundCount,
          selectedTimeLimitSeconds,
        }),
      })
      return nextRoundCount
    })
  }, [enqueueClientMessage, minimumRoundCount, selectedTimeLimitSeconds])

  const selectTimeLimit = useCallback(
    (timeLimitSeconds: FlipbookTimeLimitSeconds) => {
      setSelectedTimeLimitSeconds(timeLimitSeconds)
      enqueueClientMessage({
        type: 'flipbook.settings.update',
        requestId: createRequestId('settings-update'),
        payload: toFlipbookSettings({
          minimumRoundCount,
          participantCount: FLIPBOOK_PARTICIPANTS.length,
          roundCount,
          selectedTimeLimitSeconds: timeLimitSeconds,
        }),
      })
    },
    [enqueueClientMessage, minimumRoundCount, roundCount],
  )

  const leaveRoom = useCallback(() => {
    enqueueClientMessage({
      type: 'flipbook.room.leave',
      requestId: createRequestId('room-leave'),
      payload: {
        roomId,
        userUuid,
      },
    })
    setCurrentStep('lobby')
  }, [enqueueClientMessage, roomId, userUuid])

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
    connectionStatus,
    outboundMessages,
    sessionSnapshot,
    drawingBoard,
    createRoom,
    enterRoom,
    selectStep,
    goToNextStep,
    startGame,
    completeRound,
    selectTimeLimit,
    increaseRoundCount,
    decreaseRoundCount,
    leaveRoom,
    setIsGifPlaying,
    showPreviousResultFrame,
    showNextResultFrame,
  }
}
