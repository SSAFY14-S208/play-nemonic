'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { HTTPError } from 'ky'
import {
  ApiError,
  deleteFlipbookRoomParticipantMe,
  getFlipbookRoom,
  getFlipbookRoomAssignmentMe,
  getFlipbookRoomResult,
  postFileConfirm,
  postFilePresign,
  postFlipbookRoom,
  postFlipbookRoomRoundFrame,
  postFlipbookRoomStart,
  postInvite,
  patchFlipbookRoomSettings,
  putFileToPresignedUrl,
} from '@/shared/apis'
import { useDrawingBoard } from '@/shared/hooks'
import { useUserStore } from '@/shared/stores'
import type {
  ApiResponse,
  DrawingLine,
  FlipbookAssignmentResponse,
  FlipbookFrameSubmitResponse,
  FlipbookResultFrameResponse,
  FlipbookRealtimeEvent,
  FlipbookResultItemResponse,
  FlipbookRoomParticipantResponse,
  FlipbookRoomStateResponse,
} from '@/shared/types'
import { getDisplayImageUrl, renderLinesToRasterCanvas } from '@/shared/utils'
import {
  FLIPBOOK_BACKGROUND_COLOR,
  FLIPBOOK_BOARD_SIZE,
  FLIPBOOK_COLORS,
  FLIPBOOK_TIME_LIMITS_SECONDS,
} from '../constants'
import type {
  FlipbookFrame,
  FlipbookParticipant,
  FlipbookStep,
  FlipbookTimeLimitSeconds,
} from '../types'
import { createLocalFlipbookParticipant } from '../flipbookSessionMapper'
import { useFlipbookRealtimeConnection } from './useFlipbookRealtimeConnection'
import { useFlipbookResultPlayback } from './useFlipbookResultPlayback'
import { useFlipbookTimer } from './useFlipbookTimer'

const FLIPBOOK_FILE_CONTENT_TYPE = 'image/png'
const FLIPBOOK_FILE_PURPOSE = 'FLIPBOOK'
const RESULT_POLLING_INTERVAL_MS = 1500
const ASSIGNMENT_RETRY_DELAYS_MS = [1000, 2000, 3000, 5000]

type FlipbookNicknamePendingAction = 'createRoom' | 'enterRoom'

interface UseFlipbookOptions {
  routeStep?: FlipbookStep
  onStepChange?: (
    step: FlipbookStep,
    options?: {
      roomCode?: string | null
      replace?: boolean
    },
  ) => void
}

function isFlipbookTimeLimitSeconds(seconds: number): seconds is FlipbookTimeLimitSeconds {
  return FLIPBOOK_TIME_LIMITS_SECONDS.includes(seconds as FlipbookTimeLimitSeconds)
}

function toFlipbookTimeLimitSeconds(seconds: number): FlipbookTimeLimitSeconds {
  return isFlipbookTimeLimitSeconds(seconds) ? seconds : FLIPBOOK_TIME_LIMITS_SECONDS[1]
}

function toParticipant(
  participant: FlipbookRoomParticipantResponse,
  currentUserUuid: string | null,
): FlipbookParticipant {
  return {
    id: participant.userUuid,
    userUuid: participant.userUuid,
    name: `${participant.nickname}${participant.userUuid === currentUserUuid ? ' (나)' : ''}`,
    avatar: participant.host ? '👑' : '🙂',
    isHost: participant.host,
  }
}

function createImageLineFromUrl(id: string, imageUrl: string): DrawingLine {
  return {
    id,
    kind: 'fill',
    color: 'transparent',
    strokeWidth: 0,
    points: [],
    imageDataUrl: getDisplayImageUrl(imageUrl) ?? imageUrl,
  }
}

function createPreviousFrameLinesFromAssignment(
  assignment: FlipbookAssignmentResponse,
): DrawingLine[] {
  const hint = assignment.hint
  const hintImageUrl = hint?.imageUrl ?? hint?.url
  if (!hint || !hintImageUrl || hint.empty) return []

  return [
    createImageLineFromUrl(
      `flipbook-hint-${hint.flipbookIndex}-${hint.frameIndex}-${hint.round}`,
      hintImageUrl,
    ),
  ]
}

function getServerRoundCount({
  roomState,
  fallback,
}: {
  roomState?: FlipbookRoomStateResponse | null
  fallback?: number | null
}) {
  return roomState?.totalRounds ?? fallback ?? null
}

function getRoomParticipantCount(roomState: FlipbookRoomStateResponse | null) {
  if (!roomState) return 1

  return Math.max(1, roomState.participantCount, roomState.participants.length)
}

function getDrawingTurnCount(cycleRoundCount: number | null, participantCount: number) {
  if (cycleRoundCount === null) return null

  return cycleRoundCount * Math.max(1, participantCount)
}

function getAssignmentKey(assignment: FlipbookAssignmentResponse) {
  return `${assignment.currentRound}:${assignment.flipbookIndex}:${assignment.frameIndex}`
}

function getResultFrames(result: FlipbookResultItemResponse | null): FlipbookFrame[] {
  if (!result) return []

  const sortedFrames = [...result.frames].sort(
    (firstFrame, secondFrame) => firstFrame.frameIndex - secondFrame.frameIndex,
  )
  const singleFrameFallbackImageUrl =
    sortedFrames.length === 1
      ? selectFirstImageUrl(result.firstImageUrl, result.thumbnailUrl)
      : ''

  return sortedFrames.map((frame) => ({
    id: `${result.flipbookIndex}-${frame.frameIndex}`,
    index: frame.frameIndex,
    drawnByUserUuid: frame.drawnByUserUuid,
    drawnBy: frame.drawnByNickname,
    participantAvatar: '🙂',
    lines: [],
    imageUrl: selectFirstImageUrl(frame.imageUrl, singleFrameFallbackImageUrl),
  }))
}

function hasImageUrl(imageUrl: string | null | undefined) {
  return Boolean(imageUrl?.trim())
}

function selectFirstImageUrl(...imageUrls: Array<string | null | undefined>) {
  return imageUrls.find(hasImageUrl) ?? ''
}

function shouldReplaceResultFrame(
  currentFrame: FlipbookResultFrameResponse,
  nextFrame: FlipbookResultFrameResponse,
) {
  return !hasImageUrl(currentFrame.imageUrl) && hasImageUrl(nextFrame.imageUrl)
}

function mergeResultItemGroup(
  flipbookIndex: number,
  resultItems: FlipbookResultItemResponse[],
): FlipbookResultItemResponse {
  const [baseResultItem] = resultItems
  const framesByFrameIndex = new Map<number, FlipbookResultFrameResponse>()

  resultItems.forEach((resultItem) => {
    resultItem.frames.forEach((frame) => {
      const currentFrame = framesByFrameIndex.get(frame.frameIndex)

      if (!currentFrame || shouldReplaceResultFrame(currentFrame, frame)) {
        framesByFrameIndex.set(frame.frameIndex, frame)
      }
    })
  })

  const frames = Array.from(framesByFrameIndex.values()).sort(
    (firstFrame, secondFrame) => firstFrame.frameIndex - secondFrame.frameIndex,
  )
  const frameImageUrls = frames.map((frame) => frame.imageUrl)
  const firstImageUrl = selectFirstImageUrl(
    ...resultItems.map((resultItem) => resultItem.firstImageUrl),
    ...frameImageUrls,
  )
  const thumbnailUrl = selectFirstImageUrl(
    ...resultItems.map((resultItem) => resultItem.thumbnailUrl),
    firstImageUrl,
    ...frameImageUrls,
  )

  return {
    ...baseResultItem,
    flipbookIndex,
    thumbnailUrl,
    firstImageUrl,
    gifUrl: selectFirstImageUrl(...resultItems.map((resultItem) => resultItem.gifUrl)),
    frames,
  }
}

function getNormalizedResultItems(
  results: FlipbookResultItemResponse[],
  participantCount: number,
) {
  const resultItemsByFlipbookIndex = new Map<number, FlipbookResultItemResponse[]>()

  results.forEach((result) => {
    const resultItems = resultItemsByFlipbookIndex.get(result.flipbookIndex) ?? []
    resultItems.push(result)
    resultItemsByFlipbookIndex.set(result.flipbookIndex, resultItems)
  })

  const duplicateFlipbookIndexes = Array.from(resultItemsByFlipbookIndex.entries())
    .filter(([, resultItems]) => resultItems.length > 1)
    .map(([flipbookIndex]) => flipbookIndex)

  const normalizedResultItems = Array.from(resultItemsByFlipbookIndex.entries())
    .sort(
      ([firstFlipbookIndex], [secondFlipbookIndex]) =>
        firstFlipbookIndex - secondFlipbookIndex,
    )
    .map(([flipbookIndex, resultItems]) => mergeResultItemGroup(flipbookIndex, resultItems))

  if (duplicateFlipbookIndexes.length > 0) {
    console.warn('플립북 결과에 중복 flipbookIndex가 있어 작품별로 병합했습니다.', {
      duplicateFlipbookIndexes,
      rawResultLength: results.length,
      normalizedResultLength: normalizedResultItems.length,
    })
  }

  if (normalizedResultItems.length > 0 && normalizedResultItems.length !== participantCount) {
    console.warn('플립북 병합 결과 수와 참여자 수가 일치하지 않습니다.', {
      normalizedResultLength: normalizedResultItems.length,
      participantCount,
    })
  }

  return normalizedResultItems.slice(0, Math.max(1, participantCount))
}

function hasConfiguredNickname(nickname: string | null) {
  return Boolean(nickname?.trim())
}

function isNicknameRequiredMessage(message: string | undefined) {
  if (!message) return false
  return message.includes('닉네임') && message.includes('설정')
}

async function getFlipbookActionError(error: unknown, fallbackRequiresNickname = false) {
  if (error instanceof ApiError) {
    return {
      message: error.message,
      requiresNickname: isNicknameRequiredMessage(error.message),
    }
  }

  if (error instanceof HTTPError) {
    try {
      const responseText = await error.response.clone().text()
      const responseBody = JSON.parse(responseText) as Partial<ApiResponse<unknown>>
      const message = responseBody.message ?? error.message

      return {
        message,
        requiresNickname:
          isNicknameRequiredMessage(message) || (fallbackRequiresNickname && error.response.status === 400),
      }
    } catch {
      return {
        message: error.message,
        requiresNickname:
          isNicknameRequiredMessage(error.message) || (fallbackRequiresNickname && error.response.status === 400),
      }
    }
  }

  return {
    message: error instanceof Error ? error.message : '플립북 요청에 실패했습니다.',
    requiresNickname: false,
  }
}

async function createCanvasBlobFromLines(lines: DrawingLine[]) {
  const renderedCanvas = await renderLinesToRasterCanvas({
    backgroundColor: FLIPBOOK_BACKGROUND_COLOR,
    boardSize: FLIPBOOK_BOARD_SIZE,
    lines,
  })
  const rasterCanvas = renderedCanvas ?? document.createElement('canvas')

  if (!renderedCanvas) {
    rasterCanvas.width = FLIPBOOK_BOARD_SIZE.width
    rasterCanvas.height = FLIPBOOK_BOARD_SIZE.height
  }
  const context = rasterCanvas.getContext('2d')

  if (context) {
    context.save()
    context.globalCompositeOperation = 'destination-over'
    context.fillStyle = FLIPBOOK_BACKGROUND_COLOR
    context.fillRect(0, 0, rasterCanvas.width, rasterCanvas.height)
    context.restore()
  }

  return new Promise<Blob>((resolve, reject) => {
    rasterCanvas.toBlob((blob) => {
      if (blob) {
        resolve(blob)
        return
      }

      reject(new Error('플립북 프레임 이미지를 만들 수 없습니다.'))
    }, FLIPBOOK_FILE_CONTENT_TYPE)
  })
}

function wait(milliseconds: number) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, milliseconds)
  })
}

export function useFlipbook({
  routeStep = 'booth',
  onStepChange,
}: UseFlipbookOptions = {}) {
  const userUuid = useUserStore((state) => state.userUuid)
  const nickname = useUserStore((state) => state.nickname)
  const currentParticipant = useMemo(
    () => createLocalFlipbookParticipant({ nickname, userUuid }),
    [nickname, userUuid],
  )
  const drawingBoard = useDrawingBoard({
    boardSize: FLIPBOOK_BOARD_SIZE,
    backgroundColor: FLIPBOOK_BACKGROUND_COLOR,
    defaultColor: FLIPBOOK_COLORS[0],
    defaultStrokeWidth: 6,
  })
  const [currentStep, setCurrentStepState] = useState<FlipbookStep>(routeStep)
  const setCurrentStep = useCallback(
    (
      step: FlipbookStep,
      options?: {
        roomCode?: string | null
        replace?: boolean
      },
    ) => {
      setCurrentStepState(step)
      onStepChange?.(step, options)
    },
    [onStepChange],
  )
  const [roomCode, setRoomCode] = useState<string | null>(null)
  const [roomCodeDraft, setRoomCodeDraft] = useState('')
  const [roomState, setRoomState] = useState<FlipbookRoomStateResponse | null>(null)
  const [assignment, setAssignment] = useState<FlipbookAssignmentResponse | null>(null)
  const [previousFrameLines, setPreviousFrameLines] = useState<DrawingLine[]>([])
  const [selectedTimeLimitSeconds, setSelectedTimeLimitSeconds] =
    useState<FlipbookTimeLimitSeconds>(FLIPBOOK_TIME_LIMITS_SECONDS[1])
  const [roundCount, setRoundCount] = useState<number | null>(null)
  const [startedParticipantCount, setStartedParticipantCount] = useState<number | null>(null)
  const [submittedAssignmentKeys, setSubmittedAssignmentKeys] = useState<Set<string>>(
    () => new Set(),
  )
  const [resultItems, setResultItems] = useState<FlipbookResultItemResponse[]>([])
  const [activeResultIndex, setActiveResultIndex] = useState(0)
  const [isResultReady, setIsResultReady] = useState(false)
  const [resultCount, setResultCount] = useState(0)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isBusy, setIsBusy] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [nicknameModalOpen, setNicknameModalOpen] = useState(false)
  const [timeUpSubmitRequest, setTimeUpSubmitRequest] = useState<{
    roomCode: string
    round: number | null
    occurredAt: string
  } | null>(null)
  const isCompletingRoundRef = useRef(false)
  const linkRoomCodeHandledRef = useRef<string | null>(null)
  const assignmentRequestSequenceRef = useRef(0)
  const roundTransitionFallbackTimerRef = useRef<number | null>(null)
  const pendingNicknameActionRef = useRef<FlipbookNicknamePendingAction | null>(null)

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (!cancelled) {
        setCurrentStepState(routeStep)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [routeStep])

  const readRouteRoomCode = useCallback(() => {
    if (typeof window === 'undefined') return null

    const queryRoomCode = new URLSearchParams(window.location.search).get('roomCode')
    const normalizedRoomCode = queryRoomCode?.trim().toUpperCase() ?? ''

    return normalizedRoomCode || null
  }, [])

  const participantCount = getRoomParticipantCount(roomState)
  const participants = useMemo(
    () =>
      roomState?.participants.map((participant) => toParticipant(participant, userUuid)) ?? [
        currentParticipant,
      ],
    [currentParticipant, roomState?.participants, userUuid],
  )
  const resultOwnerNames = useMemo(
    () =>
      [...(roomState?.participants ?? [])]
        .sort(
          (firstParticipant, secondParticipant) =>
            firstParticipant.joinOrder - secondParticipant.joinOrder,
        )
        .map((participant) => participant.nickname),
    [roomState?.participants],
  )
  const displayedParticipant =
    participants.find((participant) => participant.userUuid === userUuid) ?? currentParticipant
  const cycleRoundCount = getServerRoundCount({
    roomState,
    fallback: roundCount ?? assignment?.totalRounds ?? null,
  })
  const drawingParticipantCount = startedParticipantCount ?? participantCount
  const drawingRoundCount = getDrawingTurnCount(cycleRoundCount, drawingParticipantCount)
  const activeRoundIndex = Math.max(0, (assignment?.currentRound ?? roomState?.currentRound ?? 1) - 1)
  const canStartGame = roomState?.viewer.canStart === true
  const isHost = roomState?.viewer.host === true
  const activeAssignmentKey = assignment ? getAssignmentKey(assignment) : null
  const isRoundSubmitted =
    activeAssignmentKey !== null && submittedAssignmentKeys.has(activeAssignmentKey)
  const activeResult = resultItems[activeResultIndex] ?? resultItems[0] ?? null
  const resultFrames = useMemo(() => getResultFrames(activeResult), [activeResult])
  const resultPlayback = useFlipbookResultPlayback({
    currentStep,
    frames: resultFrames,
  })

  const resetDrawingRound = useCallback(() => {
    drawingBoard.replaceLines([])
  }, [drawingBoard])

  const clearDrawingRound = useCallback(() => {
    assignmentRequestSequenceRef.current += 1
    setAssignment(null)
    setPreviousFrameLines([])
    drawingBoard.replaceLines([])
  }, [drawingBoard])

  const clearRoundTransitionFallbackTimer = useCallback(() => {
    if (roundTransitionFallbackTimerRef.current === null) return
    window.clearTimeout(roundTransitionFallbackTimerRef.current)
    roundTransitionFallbackTimerRef.current = null
  }, [])

  const refreshRoom = useCallback(
    async (
      targetRoomCode = roomCode,
      options: {
        syncStep?: boolean
      } = {},
    ) => {
      if (!targetRoomCode) return null
      const nextRoomState = await getFlipbookRoom(targetRoomCode)
      setRoomState(nextRoomState)
      setSelectedTimeLimitSeconds(toFlipbookTimeLimitSeconds(nextRoomState.timeLimitSeconds))
      setRoundCount(nextRoomState.totalRounds)
      setStartedParticipantCount((currentParticipantCount) => {
        if (nextRoomState.status === 'WAITING') return null
        if (nextRoomState.status !== 'PLAYING') return currentParticipantCount

        return currentParticipantCount ?? getRoomParticipantCount(nextRoomState)
      })
      const shouldSyncStep = options.syncStep ?? true

      if (shouldSyncStep && nextRoomState.status === 'WAITING') {
        setCurrentStep('lobby', { roomCode: targetRoomCode })
      } else if (shouldSyncStep && nextRoomState.status === 'PLAYING') {
        setCurrentStep('drawing', { roomCode: targetRoomCode })
      } else if (shouldSyncStep && nextRoomState.status === 'FINISHED') {
        setCurrentStep('result', { roomCode: targetRoomCode })
      }

      return nextRoomState
    },
    [roomCode, setCurrentStep],
  )

  const fetchAssignment = useCallback(
    async (targetRoomCode = roomCode, expectedRound?: number) => {
      if (!targetRoomCode) return null
      const requestSequence = assignmentRequestSequenceRef.current + 1
      assignmentRequestSequenceRef.current = requestSequence

      for (let attemptIndex = 0; attemptIndex <= ASSIGNMENT_RETRY_DELAYS_MS.length; attemptIndex++) {
        try {
          const nextAssignment = await getFlipbookRoomAssignmentMe(targetRoomCode)
          if (assignmentRequestSequenceRef.current !== requestSequence) return null
          if (expectedRound && nextAssignment.currentRound < expectedRound) {
            throw new Error('새 라운드 배정이 아직 준비되지 않았습니다.')
          }

          setAssignment(nextAssignment)
          setSelectedTimeLimitSeconds(toFlipbookTimeLimitSeconds(nextAssignment.timeLimitSeconds))
          setRoundCount((currentRoundCount) =>
            roomState?.totalRounds ?? currentRoundCount ?? nextAssignment.totalRounds,
          )
          resetDrawingRound()
          setPreviousFrameLines(createPreviousFrameLinesFromAssignment(nextAssignment))

          return nextAssignment
        } catch (error) {
          const retryDelayMs = ASSIGNMENT_RETRY_DELAYS_MS[attemptIndex]
          if (retryDelayMs === undefined) throw error
          await wait(retryDelayMs)
        }
      }

      return null
    },
    [resetDrawingRound, roomCode, roomState?.totalRounds],
  )

  const fetchResult = useCallback(
    async (targetRoomCode = roomCode, resultParticipantCount = participantCount) => {
      if (!targetRoomCode) return null
      const nextResult = await getFlipbookRoomResult(targetRoomCode)
      const visibleResultItems = getNormalizedResultItems(nextResult.results, resultParticipantCount)
      setResultCount(nextResult.ready ? visibleResultItems.length : nextResult.resultCount)

      if (nextResult.ready) {
        setResultItems(visibleResultItems)
        setActiveResultIndex((currentIndex) =>
          Math.min(currentIndex, Math.max(0, visibleResultItems.length - 1)),
        )
        setIsResultReady(true)
        setCurrentStep('result', { roomCode: targetRoomCode })
        resultPlayback.resetResultFrameIndex()
      }

      return nextResult
    },
    [participantCount, resultPlayback, roomCode, setCurrentStep],
  )

  const refreshPlayingRound = useCallback(
    async (targetRoomCode: string, expectedRound?: number) => {
      const nextRoomState = await refreshRoom(targetRoomCode)

      if (nextRoomState?.status === 'FINISHED') {
        clearDrawingRound()
        setCurrentStep('result', { roomCode: targetRoomCode })
        await fetchResult(targetRoomCode, nextRoomState.participantCount)
        return
      }

      if (nextRoomState?.status === 'PLAYING') {
        await fetchAssignment(targetRoomCode, expectedRound ?? nextRoomState.currentRound ?? undefined)
        setCurrentStep('drawing', { roomCode: targetRoomCode })
      }
    },
    [clearDrawingRound, fetchAssignment, fetchResult, refreshRoom, setCurrentStep],
  )

  const handleCompletedRounds = useCallback(
    async (targetRoomCode: string) => {
      const nextRoomState = await refreshRoom(targetRoomCode)
      clearDrawingRound()
      setCurrentStep('result', { roomCode: targetRoomCode })
      await fetchResult(targetRoomCode, nextRoomState?.participantCount ?? participantCount)
    },
    [clearDrawingRound, fetchResult, participantCount, refreshRoom, setCurrentStep],
  )

  const handleSubmittedFrameProgress = useCallback(
    async (targetRoomCode: string) => {
      return refreshRoom(targetRoomCode, { syncStep: false })
    },
    [refreshRoom],
  )

  const scheduleRoundTransitionFallback = useCallback(
    (targetRoomCode: string, submittedFrame: Partial<FlipbookFrameSubmitResponse>) => {
      const shouldAdvanceRound =
        submittedFrame.advanced ||
        submittedFrame.currentRoundCompleted ||
        submittedFrame.allRoundsCompleted
      if (!shouldAdvanceRound) return

      clearRoundTransitionFallbackTimer()
      roundTransitionFallbackTimerRef.current = window.setTimeout(() => {
        roundTransitionFallbackTimerRef.current = null

        if (submittedFrame.allRoundsCompleted || submittedFrame.roomStatus === 'FINISHED') {
          void handleCompletedRounds(targetRoomCode)
          return
        }

        void refreshPlayingRound(targetRoomCode, submittedFrame.nextRound ?? undefined)
      }, 500)
    },
    [clearRoundTransitionFallbackTimer, handleCompletedRounds, refreshPlayingRound],
  )

  const handleRealtimeEvent = useCallback(
    (event: FlipbookRealtimeEvent) => {
      void (async () => {
        if (event.type === 'PARTICIPANT_CONNECTED' || event.type === 'PARTICIPANT_DISCONNECTED') {
          await refreshRoom(event.roomCode, { syncStep: false })
          return
        }

        if (event.type === 'SETTINGS_CHANGED') {
          await refreshRoom(event.roomCode, { syncStep: false })
          return
        }

        if (event.type === 'GAME_STARTED') {
          clearRoundTransitionFallbackTimer()
          await refreshPlayingRound(event.roomCode)
          return
        }

        if (event.type === 'ROUND_STARTED') {
          const roundStartedData = event.data as { round?: number }
          clearRoundTransitionFallbackTimer()
          setTimeUpSubmitRequest(null)
          setIsSubmitting(false)
          await refreshPlayingRound(event.roomCode, roundStartedData.round)
          return
        }

        if (event.type === 'ROUND_TIME_UP') {
          const roundTimeUpData = event.data as { round?: number }
          const currentAssignmentSubmitted =
            assignment !== null && submittedAssignmentKeys.has(getAssignmentKey(assignment))

          if (currentAssignmentSubmitted) {
            setTimeUpSubmitRequest(null)
            setIsSubmitting(false)
            return
          }

          if (
            assignment &&
            roundTimeUpData.round !== undefined &&
            assignment.currentRound !== roundTimeUpData.round
          ) {
            return
          }

          setIsSubmitting(true)
          setTimeUpSubmitRequest({
            roomCode: event.roomCode,
            round: roundTimeUpData.round ?? null,
            occurredAt: event.occurredAt,
          })
          return
        }

        if (event.type === 'FRAME_SUBMITTED') {
          const submittedFrame = event.data as Partial<FlipbookFrameSubmitResponse>
          const nextRoomState = await handleSubmittedFrameProgress(event.roomCode)
          if (nextRoomState?.status === 'FINISHED') {
            await handleCompletedRounds(event.roomCode)
            return
          }

          if (
            assignment &&
            nextRoomState?.status === 'PLAYING' &&
            nextRoomState.currentRound !== null &&
            nextRoomState.currentRound > assignment.currentRound
          ) {
            await refreshPlayingRound(event.roomCode, nextRoomState.currentRound)
            return
          }

          scheduleRoundTransitionFallback(event.roomCode, submittedFrame)
          return
        }

        if (event.type === 'FRAME_AUTO_SUBMITTED') {
          const autoSubmittedFrame = event.data as {
            userUuid?: string
            round?: number
            assignmentStatus?: FlipbookAssignmentResponse['assignmentStatus']
          }
          if (autoSubmittedFrame.userUuid === userUuid) {
            setSubmittedAssignmentKeys((currentKeys) => {
              if (!assignment || assignment.currentRound !== autoSubmittedFrame.round) {
                return currentKeys
              }

              const nextKeys = new Set(currentKeys)
              nextKeys.add(getAssignmentKey(assignment))
              return nextKeys
            })
            setAssignment((currentAssignment) => {
              if (!currentAssignment || currentAssignment.currentRound !== autoSubmittedFrame.round) {
                return currentAssignment
              }

              return {
                ...currentAssignment,
                assignmentStatus: autoSubmittedFrame.assignmentStatus ?? 'AUTO_SUBMITTED',
              }
            })
          }
          await refreshRoom(event.roomCode, { syncStep: false })
          return
        }

        if (event.type === 'ALL_ROUNDS_COMPLETED') {
          clearRoundTransitionFallbackTimer()
          setTimeUpSubmitRequest(null)
          setIsSubmitting(false)
          await handleCompletedRounds(event.roomCode)
          return
        }

        if (event.type === 'PARTICIPANT_LEFT' || event.type === 'HOST_CHANGED' || event.type === 'PARTICIPANT_KICKED') {
          await refreshRoom(event.roomCode)
          return
        }

        if (event.type === 'ROOM_CLOSED') {
          setCurrentStep('booth')
          setRoomState(null)
          setRoomCode(null)
          setRoundCount(null)
          setStartedParticipantCount(null)
          setSubmittedAssignmentKeys(new Set())
          clearRoundTransitionFallbackTimer()
          setErrorMessage('방이 종료되었습니다.')
          return
        }

        if (event.type === 'KICKED_FROM_ROOM') {
          setCurrentStep('booth')
          setRoomState(null)
          setRoomCode(null)
          setRoundCount(null)
          setStartedParticipantCount(null)
          setSubmittedAssignmentKeys(new Set())
          clearRoundTransitionFallbackTimer()
          setErrorMessage('방에서 내보내졌습니다.')
          return
        }

        if (event.type === 'DUPLICATE_SESSION_CLOSED') {
          setErrorMessage('다른 탭에서 같은 계정으로 접속해 현재 연결이 종료되었습니다.')
          return
        }

        if (event.type === 'ERROR') {
          const errorData = event.data as { message?: string }
          setErrorMessage(errorData.message ?? '플립북 연결 중 오류가 발생했습니다.')
        }
      })()
    },
    [
      assignment,
      clearRoundTransitionFallbackTimer,
      handleCompletedRounds,
      handleSubmittedFrameProgress,
      refreshPlayingRound,
      refreshRoom,
      scheduleRoundTransitionFallback,
      setCurrentStep,
      submittedAssignmentKeys,
      userUuid,
    ],
  )

  const realtime = useFlipbookRealtimeConnection({
    enabled: currentStep !== 'booth' && Boolean(roomCode),
    roomCode,
    onEvent: handleRealtimeEvent,
  })

  const handleLocalTimerExpired = useCallback(() => {}, [])

  const timer = useFlipbookTimer({
    activeRoundIndex,
    currentStep,
    deadlineAt: assignment?.roundDeadlineAt ?? roomState?.roundDeadlineAt ?? null,
    initialRemainingSeconds: assignment?.remainingSeconds ?? null,
    selectedTimeLimitSeconds,
    onTimeExpired: handleLocalTimerExpired,
  })

  const uploadFrame = useCallback(
    async (lines: DrawingLine[], round: number) => {
      const imageBlob = await createCanvasBlobFromLines(lines)
      const presigned = await postFilePresign({
        fileName: `flipbook-${roomCode ?? 'room'}-round-${round}.png`,
        contentType: FLIPBOOK_FILE_CONTENT_TYPE,
        purpose: FLIPBOOK_FILE_PURPOSE,
        byteSize: imageBlob.size,
      })

      await putFileToPresignedUrl({
        presignedUrl: presigned.presignedUrl,
        file: imageBlob,
        contentType: FLIPBOOK_FILE_CONTENT_TYPE,
      })

      await postFileConfirm(presigned.fileId)
      return presigned.fileId
    },
    [roomCode],
  )

  const openNicknameModal = useCallback(
    (pendingAction: FlipbookNicknamePendingAction) => {
      if (userUuid) {
        useUserStore.getState().setUser(userUuid, null)
      }

      pendingNicknameActionRef.current = pendingAction
      setErrorMessage(null)
      setNicknameModalOpen(true)
    },
    [userUuid],
  )

  const performCreateRoom = useCallback(async () => {
    if (!userUuid || isBusy) return

    setIsBusy(true)
    setErrorMessage(null)

    try {
      const createdRoom = await postFlipbookRoom()
      setRoomCode(createdRoom.roomCode)
      setRoomCodeDraft(createdRoom.roomCode)
      linkRoomCodeHandledRef.current = createdRoom.roomCode
      await refreshRoom(createdRoom.roomCode)
      setCurrentStep('lobby', { roomCode: createdRoom.roomCode })
    } catch (error) {
      const actionError = await getFlipbookActionError(error, true)
      if (actionError.requiresNickname) {
        openNicknameModal('createRoom')
        return
      }

      setErrorMessage(actionError.message || '방 생성에 실패했습니다.')
    } finally {
      setIsBusy(false)
    }
  }, [isBusy, openNicknameModal, refreshRoom, setCurrentStep, userUuid])

  const performEnterRoom = useCallback(async (roomCodeOverride?: string) => {
    if (!userUuid || isBusy) return

    const targetRoomCode = (roomCodeOverride ?? roomCodeDraft).trim().toUpperCase()
    if (!targetRoomCode) {
      setErrorMessage('입장 코드를 입력해주세요.')
      return
    }

    setIsBusy(true)
    setErrorMessage(null)

    try {
      const joinedRoom = await postInvite(targetRoomCode)
      if (joinedRoom.boothType !== 'flipbook') {
        setErrorMessage('플립북 방 코드가 아닙니다.')
        return
      }

      setRoomCode(joinedRoom.roomId)
      linkRoomCodeHandledRef.current = joinedRoom.roomId
      await refreshRoom(joinedRoom.roomId)
    } catch (error) {
      const actionError = await getFlipbookActionError(error, true)
      if (actionError.requiresNickname) {
        setRoomCodeDraft(targetRoomCode)
        openNicknameModal('enterRoom')
        return
      }

      setErrorMessage(actionError.message || '방 입장에 실패했습니다.')
    } finally {
      setIsBusy(false)
    }
  }, [isBusy, openNicknameModal, refreshRoom, roomCodeDraft, userUuid])

  const createRoom = useCallback(() => {
    if (!hasConfiguredNickname(nickname)) {
      openNicknameModal('createRoom')
      return
    }

    void performCreateRoom()
  }, [nickname, openNicknameModal, performCreateRoom])

  const enterRoom = useCallback(() => {
    if (!hasConfiguredNickname(nickname)) {
      openNicknameModal('enterRoom')
      return
    }

    void performEnterRoom()
  }, [nickname, openNicknameModal, performEnterRoom])

  const continuePendingNicknameAction = useCallback(() => {
    const pendingAction = pendingNicknameActionRef.current
    pendingNicknameActionRef.current = null

    if (pendingAction === 'createRoom') {
      void performCreateRoom()
      return
    }

    if (pendingAction === 'enterRoom') {
      void performEnterRoom()
    }
  }, [performCreateRoom, performEnterRoom])

  const startGame = useCallback(async () => {
    if (!roomCode || isBusy || !canStartGame) return

    setIsBusy(true)
    setErrorMessage(null)

    try {
      const startedRoom = await postFlipbookRoomStart(roomCode)
      setRoomState(startedRoom)
      setSelectedTimeLimitSeconds(toFlipbookTimeLimitSeconds(startedRoom.timeLimitSeconds))
      setRoundCount(startedRoom.totalRounds)
      setStartedParticipantCount(getRoomParticipantCount(startedRoom))
      setSubmittedAssignmentKeys(new Set())
      setCurrentStep('drawing', { roomCode })
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '게임 시작에 실패했습니다.')
    } finally {
      setIsBusy(false)
    }
  }, [canStartGame, isBusy, roomCode, setCurrentStep])

  const completeRound = useCallback(async ({
    keepSubmittingUntilServerAdvance = false,
  }: {
    keepSubmittingUntilServerAdvance?: boolean
  } = {}) => {
    if (!roomCode || !assignment || isCompletingRoundRef.current) return
    const assignmentKey = getAssignmentKey(assignment)
    if (submittedAssignmentKeys.has(assignmentKey)) {
      if (!keepSubmittingUntilServerAdvance) {
        setIsSubmitting(false)
      }
      return
    }

    isCompletingRoundRef.current = true
    setIsSubmitting(true)
    setErrorMessage(null)

    try {
      const submittedLines = drawingBoard.lines
      const fileId = await uploadFrame(submittedLines, assignment.currentRound)
      const submittedFrame = await postFlipbookRoomRoundFrame(roomCode, assignment.currentRound, {
        flipbookIndex: assignment.flipbookIndex,
        frameIndex: assignment.frameIndex,
        fileId,
      })

      setAssignment((currentAssignment) =>
        currentAssignment?.currentRound === submittedFrame.round
          ? { ...currentAssignment, assignmentStatus: submittedFrame.assignmentStatus }
          : currentAssignment,
      )
      setSubmittedAssignmentKeys((currentKeys) => {
        const nextKeys = new Set(currentKeys)
        nextKeys.add(assignmentKey)
        return nextKeys
      })
      const nextRoomState = await handleSubmittedFrameProgress(roomCode)
      if (nextRoomState?.status === 'FINISHED') {
        await handleCompletedRounds(roomCode)
        return
      }

      if (
        nextRoomState?.status === 'PLAYING' &&
        nextRoomState.currentRound !== null &&
        nextRoomState.currentRound > submittedFrame.round
      ) {
        await refreshPlayingRound(roomCode, nextRoomState.currentRound)
        return
      }

      scheduleRoundTransitionFallback(roomCode, submittedFrame)
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '프레임 제출에 실패했습니다.')
      if (!keepSubmittingUntilServerAdvance) {
        await refreshRoom(roomCode)
      }
    } finally {
      isCompletingRoundRef.current = false
      if (!keepSubmittingUntilServerAdvance) {
        setIsSubmitting(false)
        setTimeUpSubmitRequest(null)
      }
    }
  }, [
    assignment,
    drawingBoard.lines,
    handleCompletedRounds,
    handleSubmittedFrameProgress,
    refreshPlayingRound,
    refreshRoom,
    roomCode,
    scheduleRoundTransitionFallback,
    submittedAssignmentKeys,
    uploadFrame,
  ])

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (!timeUpSubmitRequest || !assignment) return
      if (timeUpSubmitRequest.roomCode !== roomCode) return
      if (timeUpSubmitRequest.round !== null && timeUpSubmitRequest.round !== assignment.currentRound) {
        return
      }

      await completeRound({ keepSubmittingUntilServerAdvance: true })
      if (!cancelled) {
        setTimeUpSubmitRequest(null)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [assignment, completeRound, roomCode, timeUpSubmitRequest])

  const selectTimeLimit = useCallback(
    (timeLimitSeconds: FlipbookTimeLimitSeconds) => {
      if (!roomCode || !isHost) return

      setSelectedTimeLimitSeconds(timeLimitSeconds)
      void (async () => {
        try {
          const updatedRoom = await patchFlipbookRoomSettings(roomCode, { timeLimitSeconds })
          setRoomState(updatedRoom)
          setRoundCount(updatedRoom.totalRounds)
        } catch (error) {
          setErrorMessage(error instanceof Error ? error.message : '제한 시간 변경에 실패했습니다.')
        }
      })()
    },
    [isHost, roomCode],
  )

  const selectRoundCount = useCallback(
    (nextRoundCount: number) => {
      if (!roomCode || !isHost || nextRoundCount < 1) return

      void (async () => {
        try {
          const updatedRoom = await patchFlipbookRoomSettings(roomCode, {
            roundCount: nextRoundCount,
          })
          setRoomState(updatedRoom)
          setRoundCount(updatedRoom.totalRounds)
        } catch (error) {
          setErrorMessage(error instanceof Error ? error.message : '라운드 수 변경에 실패했습니다.')
        }
      })()
    },
    [isHost, roomCode],
  )

  const leaveRoom = useCallback(() => {
    void (async () => {
      if (!roomCode) {
        setCurrentStep('booth')
        return
      }

      if (roomState?.status === 'WAITING') {
        try {
          await deleteFlipbookRoomParticipantMe(roomCode)
        } catch {
          // 이미 방이 닫혔거나 참여 상태가 아닐 수 있으므로 화면 이탈은 계속 진행한다.
        }
      }

      setRoomCode(null)
      setRoomState(null)
      setRoundCount(null)
      setStartedParticipantCount(null)
      setSubmittedAssignmentKeys(new Set())
      clearRoundTransitionFallbackTimer()
      clearDrawingRound()
      setCurrentStep('booth')
    })()
  }, [
    clearDrawingRound,
    clearRoundTransitionFallbackTimer,
    roomCode,
    roomState?.status,
    setCurrentStep,
  ])

  const selectStep = useCallback(
    (step: FlipbookStep) => {
      if (step === 'booth') {
        setRoomCode(null)
        setRoomState(null)
        setRoundCount(null)
        setStartedParticipantCount(null)
        setSubmittedAssignmentKeys(new Set())
        clearRoundTransitionFallbackTimer()
        clearDrawingRound()
        setResultItems([])
        setActiveResultIndex(0)
        setIsResultReady(false)
      }

      if (step === 'drawing') {
        resetDrawingRound()
      }

      if (step === 'result') {
        resultPlayback.resetResultFrameIndex()
      }

      setCurrentStep(step)
    },
    [
      clearDrawingRound,
      clearRoundTransitionFallbackTimer,
      resetDrawingRound,
      resultPlayback,
      setCurrentStep,
    ],
  )

  useEffect(() => {
    let cancelled = false

    void (async () => {
      const targetRoomCode = readRouteRoomCode()
      if (!targetRoomCode || cancelled) return
      if (!targetRoomCode) return

      setRoomCodeDraft(targetRoomCode)

      if (!userUuid || linkRoomCodeHandledRef.current === targetRoomCode || cancelled) return

      linkRoomCodeHandledRef.current = targetRoomCode

      if (!hasConfiguredNickname(nickname)) {
        openNicknameModal('enterRoom')
        return
      }

      await performEnterRoom(targetRoomCode)
    })()

    return () => {
      cancelled = true
    }
  }, [
    nickname,
    openNicknameModal,
    performEnterRoom,
    readRouteRoomCode,
    userUuid,
  ])

  useEffect(() => {
    if (currentStep !== 'result' || !roomCode || isResultReady) return

    let cancelled = false
    const pollResult = async () => {
      try {
        const nextResult = await fetchResult(roomCode)
        if (!nextResult?.ready && !cancelled) {
          window.setTimeout(pollResult, RESULT_POLLING_INTERVAL_MS)
        }
      } catch {
        if (!cancelled) {
          window.setTimeout(pollResult, RESULT_POLLING_INTERVAL_MS)
        }
      }
    }

    void pollResult()

    return () => {
      cancelled = true
    }
  }, [currentStep, fetchResult, isResultReady, roomCode])

  const selectResult = useCallback(
    (resultIndex: number) => {
      setActiveResultIndex(Math.min(Math.max(0, resultIndex), Math.max(0, resultItems.length - 1)))
      resultPlayback.resetResultFrameIndex()
      resultPlayback.setIsGifPlaying(true)
    },
    [resultItems.length, resultPlayback],
  )

  return {
    currentStep,
    roomCode,
    roomCodeDraft,
    selectedTimeLimitSeconds,
    roundCount: cycleRoundCount,
    drawingRoundCount,
    activeRoundIndex,
    remainingSeconds: timer.remainingSeconds,
    currentParticipant: displayedParticipant,
    participants,
    participantCount,
    maxParticipants: roomState?.maxParticipants ?? 12,
    previousFrameLines,
    resultItems,
    resultOwnerNames,
    activeResultIndex,
    frames: resultFrames,
    gifUrl: activeResult?.gifUrl ?? null,
    resultCount,
    resultFrameIndex: resultPlayback.resultFrameIndex,
    activeResultFrame: resultPlayback.activeResultFrame,
    isGifPlaying: resultPlayback.isGifPlaying,
    canGoPreviousResultFrame: resultPlayback.canGoPreviousResultFrame,
    canGoNextResultFrame: resultPlayback.canGoNextResultFrame,
    connectionStatus: realtime.connectionStatus,
    canStartGame,
    isHost,
    isBusy,
    isSubmitting,
    isRoundSubmitted,
    nicknameModalOpen,
    errorMessage,
    drawingBoard,
    setRoomCodeDraft,
    setNicknameModalOpen,
    continuePendingNicknameAction,
    createRoom,
    enterRoom,
    selectStep,
    startGame,
    completeRound,
    selectTimeLimit,
    selectRoundCount,
    leaveRoom,
    setIsGifPlaying: resultPlayback.setIsGifPlaying,
    showResultFrame: resultPlayback.showResultFrame,
    showPreviousResultFrame: resultPlayback.showPreviousResultFrame,
    showNextResultFrame: resultPlayback.showNextResultFrame,
    selectResult,
  }
}
