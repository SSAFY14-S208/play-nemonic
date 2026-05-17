'use client'

import { useCallback, useEffect, useMemo, useRef, useState, useTransition } from 'react'
import { HTTPError } from 'ky'
import {
  deleteFlipbookRoomParticipantMe,
  getFlipbookRoom,
  getFlipbookRoomAssignmentMe,
  getFlipbookRoomResult,
  postFileConfirm,
  postFilePresign,
  postFlipbookRoom,
  postFlipbookRoomClose,
  postFlipbookRoomKick,
  postFlipbookRoomRoundFrame,
  postFlipbookRoomStart,
  postInvite,
  patchFlipbookRoomSettings,
  putFileToPresignedUrl,
} from '@/shared/apis'
import { DRAWING_COLORS, DEFAULT_DRAWING_STROKE_WIDTH } from '@/shared/constants'
import { useDrawingBoard, useFunnelEntry } from '@/shared/hooks'
import { completeFunnelStep, logEvent, reachFunnelGoal } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'
import type {
  DrawingLine,
  FlipbookAssignmentResponse,
  FlipbookBlockedReason,
  FlipbookFrameSubmitResponse,
  FlipbookResultItemResponse,
  FlipbookRoomStateResponse,
} from '@/shared/types'
import {
  FLIPBOOK_BACKGROUND_COLOR,
  FLIPBOOK_BOARD_SIZE,
} from '../constants'
import type {
  FlipbookStep,
  FlipbookTimeLimitSeconds,
} from '../types'
import {
  createCanvasBlobFromLines,
  createFlipbookDummyResultItems,
  createLocalFlipbookParticipant,
  createPreviousFrameLinesFromAssignment,
  FLIPBOOK_FILE_CONTENT_TYPE,
  FLIPBOOK_FILE_PURPOSE,
  getAssignmentKey,
  getFlipbookTimeLimitOptions,
  getFlipbookActionError,
  getNormalizedResultItems,
  getResultFrames,
  getRoomParticipantCount,
  getServerRoundCount,
  hasConfiguredNickname,
  toFlipbookParticipant,
  toFlipbookTimeLimitSeconds,
} from '../utils'
import { useFlipbookRealtimeConnection } from './useFlipbookRealtimeConnection'
import { useFlipbookRealtimeEventHandler } from './useFlipbookRealtimeEventHandler'
import { useFlipbookResultPlayback } from './useFlipbookResultPlayback'
import { useFlipbookTimer } from './useFlipbookTimer'

const RESULT_POLLING_INTERVAL_MS = 1500
const SUBMITTED_ROUND_POLLING_INTERVAL_MS = 5000
const ASSIGNMENT_RETRY_DELAYS_MS = [1000, 2000, 3000, 5000]
const SUBMITTED_DRAWING_LINES_STORAGE_KEY = 'flipbook-submitted-drawing-lines:v1'
const handledRouteRoomCodes = new Set<string>()
const BLOCKED_REASON_MESSAGE: Record<FlipbookBlockedReason, string> = {
  ROOM_FULL: '정원이 가득 찬 플립북 방입니다.',
  GAME_IN_PROGRESS: '이미 게임이 진행 중인 방입니다.',
  KICKED: '방에서 내보내져 다시 입장할 수 없습니다.',
  RECONNECT_EXPIRED: '재접속 가능 시간이 지나 입장할 수 없습니다.',
  ROOM_FINISHED: '이미 종료된 플립북 방입니다.',
  ROOM_CLOSED: '종료된 플립북 방입니다.',
}

type FlipbookNicknamePendingAction = 'createRoom' | 'enterRoom'

function isFlipbookAssignmentSubmitted(assignment: FlipbookAssignmentResponse | null) {
  return (
    assignment?.assignmentStatus === 'SUBMITTED' ||
    assignment?.assignmentStatus === 'AUTO_SUBMITTED'
  )
}

function getSubmittedDrawingLinesKey({
  assignment,
  roomCode,
  userUuid,
}: {
  assignment: FlipbookAssignmentResponse
  roomCode: string
  userUuid: string | null
}) {
  return [
    roomCode,
    userUuid ?? 'anonymous',
    assignment.currentRound,
    assignment.flipbookIndex,
    assignment.frameIndex,
  ].join(':')
}

function isSubmittedFrameForAssignment({
  assignment,
  submittedFrame,
}: {
  assignment: FlipbookAssignmentResponse
  submittedFrame: Pick<FlipbookFrameSubmitResponse, 'round' | 'flipbookIndex' | 'frameIndex'>
}) {
  return (
    assignment.currentRound === submittedFrame.round &&
    assignment.flipbookIndex === submittedFrame.flipbookIndex &&
    assignment.frameIndex === submittedFrame.frameIndex
  )
}

function readSubmittedDrawingLines(
  storageKey: string,
): DrawingLine[] | null {
  if (typeof window === 'undefined') return null

  try {
    const rawStorageValue = window.localStorage.getItem(SUBMITTED_DRAWING_LINES_STORAGE_KEY)
    if (!rawStorageValue) return null

    const storedDrawingLinesByKey = JSON.parse(rawStorageValue) as Record<string, DrawingLine[]>
    const storedDrawingLines = storedDrawingLinesByKey[storageKey]

    return Array.isArray(storedDrawingLines) ? storedDrawingLines : null
  } catch {
    return null
  }
}

function writeSubmittedDrawingLines(storageKey: string, lines: DrawingLine[]) {
  if (typeof window === 'undefined') return

  try {
    const rawStorageValue = window.localStorage.getItem(SUBMITTED_DRAWING_LINES_STORAGE_KEY)
    const storedDrawingLinesByKey = rawStorageValue
      ? (JSON.parse(rawStorageValue) as Record<string, DrawingLine[]>)
      : {}

    window.localStorage.setItem(
      SUBMITTED_DRAWING_LINES_STORAGE_KEY,
      JSON.stringify({
        ...storedDrawingLinesByKey,
        [storageKey]: lines,
      }),
    )
  } catch {
    // Waiting preview is best-effort; upload/submission remains the source of truth.
  }
}

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

function wait(milliseconds: number) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, milliseconds)
  })
}

function isDummyResultPreviewRoute() {
  if (typeof window === 'undefined') return false

  const searchParams = new URLSearchParams(window.location.search)

  return searchParams.get('dummyResult') === '1' || searchParams.get('mockResult') === '1'
}

function markRouteRoomCodesHandled(...roomCodes: Array<string | null | undefined>) {
  roomCodes.forEach((roomCode) => {
    const normalizedRoomCode = roomCode?.trim().toUpperCase()
    if (normalizedRoomCode) {
      handledRouteRoomCodes.add(normalizedRoomCode)
    }
  })
}

function hasRouteRoomCodeHandled(roomCode: string | null) {
  return roomCode !== null && handledRouteRoomCodes.has(roomCode)
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
    defaultColor: DRAWING_COLORS[0],
    defaultStrokeWidth: DEFAULT_DRAWING_STROKE_WIDTH,
  })
  const replaceDrawingLines = drawingBoard.replaceLines
  // FlipbookPage가 booth + session 라우트에 양쪽으로 마운트되므로(layout.tsx 공유),
  // 진입 step이 booth일 때만 funnel을 시작한다. lobby/drawing/result로 직접 진입한
  // 경우(예: 새로고침)는 funnel을 새로 시작하지 않는다 — 진행 중 funnel 정합성
  // 보존이 우선.
  useFunnelEntry('flipbook_room_creation', { enabled: routeStep === 'booth' })

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
    useState<FlipbookTimeLimitSeconds>(45)
  const [timeLimitOptions, setTimeLimitOptions] = useState<FlipbookTimeLimitSeconds[]>([])
  const [roundCount, setRoundCount] = useState<number | null>(null)
  const [, setStartedParticipantCount] = useState<number | null>(null)
  const [submittedFrameCount, setSubmittedFrameCount] = useState(0)
  const [submissionTotalCount, setSubmissionTotalCount] = useState(0)
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
  const [isUpdatingTimeLimit, startTimeLimitTransition] = useTransition()
  const [nicknameModalOpen, setNicknameModalOpen] = useState(false)
  const [timeUpSubmitRequest, setTimeUpSubmitRequest] = useState<{
    roomCode: string
    round: number
    assignmentKey: string
    roundDeadlineAt: string
    occurredAt: string
  } | null>(null)
  const [isDummyResultPreview, setIsDummyResultPreview] = useState(false)
  const isCompletingRoundRef = useRef(false)
  const linkRoomCodeHandledRef = useRef<string | null>(null)
  const assignmentRequestSequenceRef = useRef(0)
  const actionRequestSequenceRef = useRef(0)
  const createRoomRequestInFlightRef = useRef(false)
  const roundTransitionFallbackTimerRef = useRef<number | null>(null)
  const pendingNicknameActionRef = useRef<FlipbookNicknamePendingAction | null>(null)
  const pendingTimeLimitSecondsRef = useRef<FlipbookTimeLimitSeconds | null>(null)
  const connectedRoomProgressSyncRef = useRef<string | null>(null)
  const activeRoomProgressSyncRequestRef = useRef<{
    roomCode: string
    request: Promise<FlipbookRoomStateResponse | null>
  } | null>(null)
  // result goal은 fetchResult polling이 ready=true를 처음 만나는 시점에만 1회 발사.
  const resultGoalFiredRef = useRef(false)

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
    if (isDummyResultPreviewRoute()) return null

    const queryRoomCode = new URLSearchParams(window.location.search).get('roomCode')
    const normalizedRoomCode = queryRoomCode?.trim().toUpperCase() ?? ''

    return normalizedRoomCode || null
  }, [])

  const startActionRequest = useCallback(() => {
    actionRequestSequenceRef.current += 1

    return actionRequestSequenceRef.current
  }, [])

  const isCurrentActionRequest = useCallback((requestSequence: number) => {
    return actionRequestSequenceRef.current === requestSequence
  }, [])

  const participantCount = getRoomParticipantCount(roomState)
  const displayedSubmissionTotalCount = submissionTotalCount
  const displayedSubmittedFrameCount = Math.min(
    submittedFrameCount,
    displayedSubmissionTotalCount,
  )
  const participants = useMemo(
    () =>
      roomState?.participants.map((participant) => toFlipbookParticipant(participant)) ?? [
        currentParticipant,
      ],
    [currentParticipant, roomState?.participants],
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
  const perParticipantRoundCount = getServerRoundCount({
    roomState,
    fallback: roundCount ?? assignment?.totalRounds ?? null,
  })
  const drawingRoundCount = perParticipantRoundCount
  const activeRoundIndex = Math.max(0, (assignment?.currentRound ?? roomState?.currentRound ?? 1) - 1)
  const isWaitingRoom = roomState?.status === 'WAITING'
  const isRoomParticipant = roomState?.viewer.participant === true
  const canStartGame = isWaitingRoom && roomState?.viewer.canStart === true
  const isHost = roomState?.viewer.host === true
  const activeAssignmentKey = assignment ? getAssignmentKey(assignment) : null
  const isServerAssignmentSubmitted = isFlipbookAssignmentSubmitted(assignment)
  const isRoundSubmitted =
    activeAssignmentKey !== null &&
    (submittedAssignmentKeys.has(activeAssignmentKey) || isServerAssignmentSubmitted)
  const activeResult = resultItems[activeResultIndex] ?? resultItems[0] ?? null
  const resultFrames = useMemo(() => getResultFrames(activeResult), [activeResult])
  const resultPlayback = useFlipbookResultPlayback({
    currentStep,
    frames: resultFrames,
  })
  const resetResultPlaybackFrameIndex = resultPlayback.resetResultFrameIndex

  const showReadyResult = useCallback(
    ({
      resultItems: readyResultItems,
      resultParticipantCount,
      targetRoomCode,
      shouldSyncRoute = true,
      shouldTrackGoal = true,
    }: {
      resultItems: FlipbookResultItemResponse[]
      resultParticipantCount: number
      targetRoomCode: string | null
      shouldSyncRoute?: boolean
      shouldTrackGoal?: boolean
    }) => {
      const visibleResultItems = getNormalizedResultItems(
        readyResultItems,
        resultParticipantCount,
      )

      setResultItems(visibleResultItems)
      setActiveResultIndex((currentIndex) =>
        Math.min(currentIndex, Math.max(0, visibleResultItems.length - 1)),
      )
      setResultCount(visibleResultItems.length)

      if (shouldTrackGoal && targetRoomCode && !resultGoalFiredRef.current) {
        resultGoalFiredRef.current = true
        reachFunnelGoal('result_viewed', {
          content_type: 'flipbook',
          room_id: targetRoomCode,
        })
      }

      setIsResultReady(true)
      if (shouldSyncRoute && targetRoomCode) {
        setCurrentStep('result', { roomCode: targetRoomCode })
      } else {
        setCurrentStepState('result')
      }
      resetResultPlaybackFrameIndex()
    },
    [resetResultPlaybackFrameIndex, setCurrentStep],
  )

  useEffect(() => {
    let cancelled = false

    void (async () => {
      const shouldUseDummyResultPreview = routeStep === 'result' && isDummyResultPreviewRoute()
      if (!shouldUseDummyResultPreview) {
        if (!cancelled) {
          setIsDummyResultPreview(false)
        }
        return
      }

      const dummyResultItems = createFlipbookDummyResultItems()
      if (cancelled) return

      setIsDummyResultPreview(true)
      setRoomCode(null)
      setRoomCodeDraft('')
      setRoomState(null)
      setRoundCount(null)
      setTimeLimitOptions([])
      setStartedParticipantCount(null)
      setSubmittedAssignmentKeys(new Set())
      setAssignment(null)
      setPreviousFrameLines([])
      setErrorMessage(null)
      showReadyResult({
        resultItems: dummyResultItems,
        resultParticipantCount: dummyResultItems.length,
        targetRoomCode: null,
        shouldSyncRoute: false,
        shouldTrackGoal: false,
      })
    })()

    return () => {
      cancelled = true
    }
  }, [routeStep, showReadyResult])

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

  const resetRoomSession = useCallback(
    ({
      clearRoomCodeDraft = false,
      clearResult = false,
      clearError = false,
    }: {
      clearRoomCodeDraft?: boolean
      clearResult?: boolean
      clearError?: boolean
    } = {}) => {
      setRoomCode(null)
      if (clearRoomCodeDraft) {
        setRoomCodeDraft('')
      }
      setRoomState(null)
      setRoundCount(null)
      setTimeLimitOptions([])
      setStartedParticipantCount(null)
      setSubmittedFrameCount(0)
      setSubmissionTotalCount(0)
      setSubmittedAssignmentKeys(new Set())
      setTimeUpSubmitRequest(null)
      setIsSubmitting(false)
      pendingTimeLimitSecondsRef.current = null
      clearRoundTransitionFallbackTimer()
      clearDrawingRound()

      if (clearResult) {
        setResultItems([])
        setActiveResultIndex(0)
        setIsResultReady(false)
        setResultCount(0)
        resultGoalFiredRef.current = false
      }

      if (clearError) {
        setErrorMessage(null)
      }
    },
    [clearDrawingRound, clearRoundTransitionFallbackTimer],
  )

  const refreshRoom = useCallback(
    async (
      targetRoomCode = roomCode,
      options: {
        syncStep?: boolean
        actionRequestSequence?: number
      } = {},
    ) => {
      if (!targetRoomCode) return null
      const nextRoomState = await getFlipbookRoom(targetRoomCode)
      if (
        options.actionRequestSequence !== undefined &&
        !isCurrentActionRequest(options.actionRequestSequence)
      ) {
        return null
      }

      const nextTimeLimitSeconds = toFlipbookTimeLimitSeconds(nextRoomState.timeLimitSeconds)
      const pendingTimeLimitSeconds = pendingTimeLimitSecondsRef.current
      setRoomState(nextRoomState)
      if (
        pendingTimeLimitSeconds === null ||
        pendingTimeLimitSeconds === nextTimeLimitSeconds ||
        nextRoomState.status !== 'WAITING'
      ) {
        pendingTimeLimitSecondsRef.current = null
        setSelectedTimeLimitSeconds(nextTimeLimitSeconds)
      }
      setTimeLimitOptions(getFlipbookTimeLimitOptions(nextRoomState))
      setRoundCount(nextRoomState.totalRounds)
      setStartedParticipantCount((currentParticipantCount) => {
        if (nextRoomState.status === 'WAITING') return null
        if (nextRoomState.status !== 'PLAYING') return currentParticipantCount

        return currentParticipantCount ?? getRoomParticipantCount(nextRoomState)
      })
      const shouldSyncStep = options.syncStep ?? true

      if (shouldSyncStep && !nextRoomState.viewer.participant) {
        setCurrentStep('booth')
        return nextRoomState
      }

      if (shouldSyncStep && nextRoomState.status === 'WAITING') {
        setCurrentStep('lobby', { roomCode: targetRoomCode })
      } else if (shouldSyncStep && nextRoomState.status === 'PLAYING') {
        setCurrentStep('drawing', { roomCode: targetRoomCode })
      } else if (
        shouldSyncStep &&
        (nextRoomState.status === 'FINALIZING' || nextRoomState.status === 'FINISHED')
      ) {
        setCurrentStep('result', { roomCode: targetRoomCode })
      } else if (shouldSyncStep && nextRoomState.status === 'CLOSED') {
        setCurrentStep('booth')
        setErrorMessage('종료된 플립북 방입니다.')
      }

      return nextRoomState
    },
    [isCurrentActionRequest, roomCode, setCurrentStep],
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

          const nextAssignmentKey = getAssignmentKey(nextAssignment)
          const currentAssignmentKey = assignment ? getAssignmentKey(assignment) : null
          const isSameAssignment = currentAssignmentKey === nextAssignmentKey
          const submittedDrawingLinesKey = getSubmittedDrawingLinesKey({
            assignment: nextAssignment,
            roomCode: targetRoomCode,
            userUuid,
          })
          const storedSubmittedLines = isFlipbookAssignmentSubmitted(nextAssignment)
            ? readSubmittedDrawingLines(submittedDrawingLinesKey)
            : null

          setAssignment(nextAssignment)
          setSelectedTimeLimitSeconds(toFlipbookTimeLimitSeconds(nextAssignment.timeLimitSeconds))
          setRoundCount((currentRoundCount) =>
            roomState?.totalRounds ?? currentRoundCount ?? nextAssignment.totalRounds,
          )
          if (isFlipbookAssignmentSubmitted(nextAssignment)) {
            setSubmittedAssignmentKeys((currentKeys) => {
              const nextKeys = new Set(currentKeys)
              nextKeys.add(nextAssignmentKey)
              return nextKeys
            })
            setIsSubmitting(false)
            setTimeUpSubmitRequest(null)
          }
          if (!isSameAssignment) {
            setSubmittedFrameCount(0)
            setSubmissionTotalCount(0)
            replaceDrawingLines(storedSubmittedLines ?? [])
            setPreviousFrameLines(createPreviousFrameLinesFromAssignment(nextAssignment))
          }

          return nextAssignment
        } catch (error) {
          const retryDelayMs = ASSIGNMENT_RETRY_DELAYS_MS[attemptIndex]
          if (retryDelayMs === undefined) throw error
          await wait(retryDelayMs)
        }
      }

      return null
    },
    [assignment, replaceDrawingLines, roomCode, roomState?.totalRounds, userUuid],
  )

  const fetchResult = useCallback(
    async (targetRoomCode = roomCode, resultParticipantCount = participantCount) => {
      if (!targetRoomCode) return null
      const nextResult = await getFlipbookRoomResult(targetRoomCode)

      if (nextResult.ready) {
        showReadyResult({
          resultItems: nextResult.results,
          resultParticipantCount,
          targetRoomCode,
        })
      } else {
        setResultCount(nextResult.resultCount)
        setIsResultReady(false)
      }

      return nextResult
    },
    [participantCount, roomCode, showReadyResult],
  )

  const enterResultMode = useCallback(
    async (targetRoomCode: string, resultParticipantCount = participantCount) => {
      clearRoundTransitionFallbackTimer()
      setTimeUpSubmitRequest(null)
      setIsSubmitting(false)
      clearDrawingRound()
      setCurrentStep('result', { roomCode: targetRoomCode })
      return fetchResult(targetRoomCode, resultParticipantCount)
    },
    [
      clearDrawingRound,
      clearRoundTransitionFallbackTimer,
      fetchResult,
      participantCount,
      setCurrentStep,
    ],
  )

  const refreshPlayingRound = useCallback(
    async (targetRoomCode: string, expectedRound?: number) => {
      const nextRoomState = await refreshRoom(targetRoomCode)

      if (nextRoomState?.status === 'FINALIZING' || nextRoomState?.status === 'FINISHED') {
        await enterResultMode(targetRoomCode, nextRoomState.participantCount)
        return
      }

      if (nextRoomState?.status === 'PLAYING') {
        await fetchAssignment(targetRoomCode, expectedRound ?? nextRoomState.currentRound ?? undefined)
        setCurrentStep('drawing', { roomCode: targetRoomCode })
      }
    },
    [enterResultMode, fetchAssignment, refreshRoom, setCurrentStep],
  )

  const handleCompletedRounds = useCallback(
    async (targetRoomCode: string) => {
      const nextRoomState = await refreshRoom(targetRoomCode)
      if (nextRoomState?.status === 'FINALIZING' || nextRoomState?.status === 'FINISHED') {
        await enterResultMode(targetRoomCode, nextRoomState.participantCount)
        return
      }

      if (nextRoomState?.status === 'PLAYING') {
        setCurrentStep('drawing', { roomCode: targetRoomCode })
      }
    },
    [enterResultMode, refreshRoom, setCurrentStep],
  )

  const syncActiveRoomProgress = useCallback(
    async (
      targetRoomCode = roomCode,
      observedAssignment: FlipbookAssignmentResponse | null = assignment,
    ) => {
      if (!targetRoomCode) return null
      if (activeRoomProgressSyncRequestRef.current?.roomCode === targetRoomCode) {
        return activeRoomProgressSyncRequestRef.current.request
      }

      const syncRequest = (async () => {
        const nextRoomState = await refreshRoom(targetRoomCode, { syncStep: false })

        if (nextRoomState?.status === 'WAITING') {
          setCurrentStep('lobby', { roomCode: targetRoomCode })
          return nextRoomState
        }

        if (nextRoomState?.status === 'PLAYING') {
          const nextRound = nextRoomState.currentRound ?? undefined
          const shouldFetchAssignment =
            !observedAssignment ||
            (nextRound !== undefined && observedAssignment.currentRound !== nextRound)

          if (shouldFetchAssignment) {
            await fetchAssignment(targetRoomCode, nextRound)
          }

          setCurrentStep('drawing', { roomCode: targetRoomCode })
          return nextRoomState
        }

        if (nextRoomState?.status === 'FINALIZING' || nextRoomState?.status === 'FINISHED') {
          await enterResultMode(targetRoomCode, nextRoomState.participantCount)
          return nextRoomState
        }

        if (nextRoomState?.status === 'CLOSED') {
          resetRoomSession({ clearResult: true })
          setCurrentStep('booth')
          setErrorMessage('종료된 플립북 방입니다.')
        }

        return nextRoomState
      })()

      activeRoomProgressSyncRequestRef.current = {
        roomCode: targetRoomCode,
        request: syncRequest,
      }

      try {
        return await syncRequest
      } finally {
        if (activeRoomProgressSyncRequestRef.current?.request === syncRequest) {
          activeRoomProgressSyncRequestRef.current = null
        }
      }
    },
    [
      assignment,
      enterResultMode,
      fetchAssignment,
      refreshRoom,
      resetRoomSession,
      roomCode,
      setCurrentStep,
    ],
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

        if (
          submittedFrame.allRoundsCompleted ||
          submittedFrame.roomStatus === 'FINALIZING' ||
          submittedFrame.roomStatus === 'FINISHED'
        ) {
          void handleCompletedRounds(targetRoomCode)
          return
        }

        void refreshPlayingRound(targetRoomCode, submittedFrame.nextRound ?? undefined)
      }, 500)
    },
    [clearRoundTransitionFallbackTimer, handleCompletedRounds, refreshPlayingRound],
  )

  const handleRealtimeEvent = useFlipbookRealtimeEventHandler({
    assignment,
    submittedAssignmentKeys,
    userUuid,
    activeRoomCode: roomCode,
    clearRoundTransitionFallbackTimer,
    clearDrawingRound,
    handleCompletedRounds,
    fetchResult,
    refreshPlayingRound,
    refreshRoom,
    scheduleRoundTransitionFallback,
    setAssignment,
    setCurrentStep,
    setErrorMessage,
    setIsSubmitting,
    setPreviousFrameLines,
    setRoomCode,
    setRoomState,
    setRoundCount,
    setSelectedTimeLimitSeconds,
    setStartedParticipantCount,
    setSubmittedFrameCount,
    setSubmissionTotalCount,
    setSubmittedAssignmentKeys,
    setTimeUpSubmitRequest,
  })

  const realtime = useFlipbookRealtimeConnection({
    enabled: !isDummyResultPreview && currentStep !== 'booth' && Boolean(roomCode) && isRoomParticipant,
    roomCode,
    onEvent: handleRealtimeEvent,
  })

  const handleLocalTimerExpired = useCallback(() => {
    if (!roomCode || currentStep !== 'drawing') return
    void syncActiveRoomProgress(roomCode, null)
  }, [currentStep, roomCode, syncActiveRoomProgress])

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
    if (!userUuid || createRoomRequestInFlightRef.current) return

    const requestSequence = startActionRequest()
    const routeRoomCode = readRouteRoomCode()
    if (routeRoomCode) {
      linkRoomCodeHandledRef.current = routeRoomCode
    }
    createRoomRequestInFlightRef.current = true
    resetRoomSession({ clearRoomCodeDraft: true, clearResult: true, clearError: true })
    setCurrentStep('booth', { replace: true })
    setIsBusy(true)
    setErrorMessage(null)

    try {
      const createdRoom = await postFlipbookRoom()
      if (!isCurrentActionRequest(requestSequence)) return

      setRoomCode(createdRoom.roomCode)
      setRoomCodeDraft(createdRoom.roomCode)
      linkRoomCodeHandledRef.current = createdRoom.roomCode
      await refreshRoom(createdRoom.roomCode, { actionRequestSequence: requestSequence })
      if (!isCurrentActionRequest(requestSequence)) return

      completeFunnelStep('nickname', 1, { content_type: 'flipbook' })
      completeFunnelStep('settings', 2, {
        content_type: 'flipbook',
        room_id: createdRoom.roomCode,
      })
      setCurrentStep('lobby', { roomCode: createdRoom.roomCode })
    } catch (error) {
      if (!isCurrentActionRequest(requestSequence)) return

      const actionError = await getFlipbookActionError(error, true)
      if (actionError.requiresNickname) {
        openNicknameModal('createRoom')
        return
      }

      setErrorMessage(actionError.message || '방 생성에 실패했습니다.')
    } finally {
      createRoomRequestInFlightRef.current = false
      if (isCurrentActionRequest(requestSequence)) {
        setIsBusy(false)
      }
    }
  }, [
    isCurrentActionRequest,
    openNicknameModal,
    readRouteRoomCode,
    refreshRoom,
    resetRoomSession,
    setCurrentStep,
    startActionRequest,
    userUuid,
  ])

  const performEnterRoom = useCallback(async (
    roomCodeOverride?: string,
    options: {
      showBusy?: boolean
    } = {},
  ) => {
    if (!userUuid || isBusy) return

    const targetRoomCode = (roomCodeOverride ?? roomCodeDraft).trim().toUpperCase()
    if (!targetRoomCode) {
      setErrorMessage('입장 코드를 입력해주세요.')
      return
    }

    const requestSequence = startActionRequest()
    const shouldShowBusy = options.showBusy ?? true
    if (shouldShowBusy) {
      setIsBusy(true)
    }
    setErrorMessage(null)

    try {
      const joinedRoom = await postInvite(targetRoomCode)
      if (!isCurrentActionRequest(requestSequence)) return

      if (joinedRoom.boothType !== 'flipbook') {
        setErrorMessage('플립북 방 코드가 아닙니다.')
        return
      }

      markRouteRoomCodesHandled(targetRoomCode, joinedRoom.roomId)
      setRoomCode(joinedRoom.roomId)
      linkRoomCodeHandledRef.current = joinedRoom.roomId
      const joinedRoomState = await refreshRoom(joinedRoom.roomId, {
        actionRequestSequence: requestSequence,
      })
      if (!isCurrentActionRequest(requestSequence)) return
      if (joinedRoomState?.status === 'PLAYING') {
        const nextAssignment = await fetchAssignment(
          joinedRoom.roomId,
          joinedRoomState.currentRound ?? undefined,
        )
        if (isFlipbookAssignmentSubmitted(nextAssignment)) {
          await syncActiveRoomProgress(joinedRoom.roomId, nextAssignment)
        }
      }

      completeFunnelStep('nickname', 1, { content_type: 'flipbook' })
      completeFunnelStep('settings', 2, {
        content_type: 'flipbook',
        room_id: joinedRoom.roomId,
      })
    } catch (error) {
      if (!isCurrentActionRequest(requestSequence)) return

      const actionError = await getFlipbookActionError(error, true)
      if (actionError.requiresNickname) {
        setRoomCodeDraft(targetRoomCode)
        openNicknameModal('enterRoom')
        return
      }

      setErrorMessage(actionError.message || '방 입장에 실패했습니다.')
    } finally {
      if (shouldShowBusy && isCurrentActionRequest(requestSequence)) {
        setIsBusy(false)
      }
    }
  }, [
    isBusy,
    isCurrentActionRequest,
    fetchAssignment,
    openNicknameModal,
    refreshRoom,
    roomCodeDraft,
    startActionRequest,
    syncActiveRoomProgress,
    userUuid,
  ])

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

  const hydrateRouteRoom = useCallback(
    async (targetRoomCode: string) => {
      const requestSequence = startActionRequest()
      setErrorMessage(null)

      try {
        const routeRoom = await getFlipbookRoom(targetRoomCode)
        if (!isCurrentActionRequest(requestSequence)) return

        if (!routeRoom.viewer.participant && !routeRoom.viewer.canJoin) {
          markRouteRoomCodesHandled(targetRoomCode, routeRoom.roomCode)
          linkRoomCodeHandledRef.current = targetRoomCode
          resetRoomSession({ clearResult: true })
          setCurrentStep('booth', { replace: true })
          setErrorMessage(
            routeRoom.viewer.blockedReason
              ? BLOCKED_REASON_MESSAGE[routeRoom.viewer.blockedReason]
              : '입장할 수 없는 플립북 방입니다.',
          )
          return
        }

        if (
          routeRoom.status === 'WAITING' &&
          routeRoom.viewer.canJoin &&
          !routeRoom.viewer.participant &&
          !hasRouteRoomCodeHandled(targetRoomCode)
        ) {
          const joinedRoom = await postInvite(targetRoomCode)
          if (!isCurrentActionRequest(requestSequence)) return

          if (joinedRoom.boothType !== 'flipbook') {
            setErrorMessage('플립북 방 코드가 아닙니다.')
            return
          }

          markRouteRoomCodesHandled(targetRoomCode, joinedRoom.roomId)
          setRoomCode(joinedRoom.roomId)
          linkRoomCodeHandledRef.current = joinedRoom.roomId
          const joinedRoomState = await refreshRoom(joinedRoom.roomId, {
            actionRequestSequence: requestSequence,
          })
          if (!isCurrentActionRequest(requestSequence)) return

          if (joinedRoomState?.status === 'PLAYING') {
            const nextAssignment = await fetchAssignment(
              joinedRoom.roomId,
              joinedRoomState.currentRound ?? undefined,
            )
            if (isFlipbookAssignmentSubmitted(nextAssignment)) {
              await syncActiveRoomProgress(joinedRoom.roomId, nextAssignment)
            }
          }
          return
        }

        markRouteRoomCodesHandled(targetRoomCode, routeRoom.roomCode)
        setRoomCode(routeRoom.roomCode)
        linkRoomCodeHandledRef.current = routeRoom.roomCode
        const nextRoomState = await refreshRoom(routeRoom.roomCode, {
          actionRequestSequence: requestSequence,
        })
        if (!isCurrentActionRequest(requestSequence)) return

        if (nextRoomState?.status === 'PLAYING') {
          const nextAssignment = await fetchAssignment(
            routeRoom.roomCode,
            nextRoomState.currentRound ?? undefined,
          )
          if (isFlipbookAssignmentSubmitted(nextAssignment)) {
            await syncActiveRoomProgress(routeRoom.roomCode, nextAssignment)
          }
        }
      } catch (error) {
        if (!isCurrentActionRequest(requestSequence)) return

        const actionError = await getFlipbookActionError(error, true)
        if (actionError.requiresNickname) {
          setRoomCodeDraft(targetRoomCode)
          openNicknameModal('enterRoom')
          return
        }

        setErrorMessage(actionError.message || '방 상태를 불러오지 못했습니다.')
      }
    },
    [
      fetchAssignment,
      isCurrentActionRequest,
      openNicknameModal,
      refreshRoom,
      resetRoomSession,
      setCurrentStep,
      startActionRequest,
      syncActiveRoomProgress,
    ],
  )

  const startGame = useCallback(async () => {
    if (!roomCode || isBusy || !canStartGame || !isWaitingRoom) return

    setIsBusy(true)
    setErrorMessage(null)

    try {
      const startedRoom = await postFlipbookRoomStart(roomCode)
      setRoomState(startedRoom)
      setSelectedTimeLimitSeconds(toFlipbookTimeLimitSeconds(startedRoom.timeLimitSeconds))
      setTimeLimitOptions(getFlipbookTimeLimitOptions(startedRoom))
      setRoundCount(startedRoom.totalRounds)
      setStartedParticipantCount(getRoomParticipantCount(startedRoom))
      setSubmittedFrameCount(0)
      setSubmissionTotalCount(0)
      setSubmittedAssignmentKeys(new Set())
      await fetchAssignment(roomCode, startedRoom.currentRound ?? undefined)
      completeFunnelStep('lobby', 3, {
        content_type: 'flipbook',
        room_id: roomCode,
        participant_count: getRoomParticipantCount(startedRoom),
      })
      setCurrentStep('drawing', { roomCode })
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '게임 시작에 실패했습니다.')
    } finally {
      setIsBusy(false)
    }
  }, [canStartGame, fetchAssignment, isBusy, isWaitingRoom, roomCode, setCurrentStep])

  const completeRound = useCallback(async ({
    keepSubmittingUntilServerAdvance = false,
  }: {
    keepSubmittingUntilServerAdvance?: boolean
  } = {}) => {
    if (!roomCode || !assignment || isCompletingRoundRef.current) return
    const submittingAssignment = assignment
    const submittingAssignmentKey = getAssignmentKey(submittingAssignment)
    if (
      submittedAssignmentKeys.has(submittingAssignmentKey) ||
      isFlipbookAssignmentSubmitted(submittingAssignment)
    ) {
      setIsSubmitting(false)
      setTimeUpSubmitRequest(null)
      return
    }

    isCompletingRoundRef.current = true
    setIsSubmitting(true)
    setErrorMessage(null)

    try {
      const submittedLines = drawingBoard.lines
      const fileId = await uploadFrame(submittedLines, submittingAssignment.currentRound)
      const submittedFrame = await postFlipbookRoomRoundFrame(roomCode, submittingAssignment.currentRound, {
        flipbookIndex: submittingAssignment.flipbookIndex,
        frameIndex: submittingAssignment.frameIndex,
        fileId,
      })
      writeSubmittedDrawingLines(
        getSubmittedDrawingLinesKey({
          assignment: submittingAssignment,
          roomCode,
          userUuid,
        }),
        submittedLines,
      )

      setSubmittedFrameCount(submittedFrame.submittedCount)
      setSubmissionTotalCount(submittedFrame.totalCount)
      setAssignment((currentAssignment) =>
        currentAssignment && isSubmittedFrameForAssignment({
          assignment: currentAssignment,
          submittedFrame,
        })
          ? { ...currentAssignment, assignmentStatus: submittedFrame.assignmentStatus }
          : currentAssignment,
      )
      setSubmittedAssignmentKeys((currentKeys) => {
        const nextKeys = new Set(currentKeys)
        nextKeys.add(submittingAssignmentKey)
        return nextKeys
      })
      completeFunnelStep('drawing', 4, {
        content_type: 'flipbook',
        room_id: roomCode,
      })
      const nextRoomState = await handleSubmittedFrameProgress(roomCode)
      if (nextRoomState?.status === 'FINALIZING' || nextRoomState?.status === 'FINISHED') {
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
      if (error instanceof HTTPError && error.response.status === 409) {
        setAssignment((currentAssignment) =>
          currentAssignment && getAssignmentKey(currentAssignment) === submittingAssignmentKey
            ? { ...currentAssignment, assignmentStatus: 'SUBMITTED' }
            : currentAssignment,
        )
        setSubmittedAssignmentKeys((currentKeys) => {
          const nextKeys = new Set(currentKeys)
          nextKeys.add(submittingAssignmentKey)
          return nextKeys
        })
        setIsSubmitting(false)
        setTimeUpSubmitRequest(null)
        return
      }

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
    userUuid,
  ])

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (!timeUpSubmitRequest || !assignment) return
      const isCurrentTimeUpRequest =
        timeUpSubmitRequest.roomCode === roomCode &&
        timeUpSubmitRequest.round === assignment.currentRound &&
        timeUpSubmitRequest.assignmentKey === getAssignmentKey(assignment) &&
        timeUpSubmitRequest.roundDeadlineAt === assignment.roundDeadlineAt
      if (!isCurrentTimeUpRequest) {
        if (!cancelled) {
          setTimeUpSubmitRequest(null)
        }
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
      if (!roomCode || !isHost || !isWaitingRoom || isUpdatingTimeLimit) return
      if (timeLimitOptions.length > 0 && !timeLimitOptions.includes(timeLimitSeconds)) return
      if (timeLimitSeconds === selectedTimeLimitSeconds) return

      const previousTimeLimitSeconds = selectedTimeLimitSeconds
      pendingTimeLimitSecondsRef.current = timeLimitSeconds
      setSelectedTimeLimitSeconds(timeLimitSeconds)
      setErrorMessage(null)
      startTimeLimitTransition(async () => {
        try {
          await patchFlipbookRoomSettings(roomCode, { timeLimitSeconds })
        } catch (error) {
          pendingTimeLimitSecondsRef.current = null
          setSelectedTimeLimitSeconds(previousTimeLimitSeconds)
          setErrorMessage(error instanceof Error ? error.message : '제한 시간 변경에 실패했습니다.')
        }
      })
    },
    [
      isHost,
      isUpdatingTimeLimit,
      isWaitingRoom,
      roomCode,
      selectedTimeLimitSeconds,
      timeLimitOptions,
    ],
  )

  const kickParticipant = useCallback(
    (targetUserUuid: string) => {
      if (!roomCode || !isHost || !isWaitingRoom || targetUserUuid === userUuid) return

      void (async () => {
        setIsBusy(true)
        setErrorMessage(null)

        try {
          await postFlipbookRoomKick(roomCode, targetUserUuid)
          await refreshRoom(roomCode, { syncStep: false })
        } catch (error) {
          setErrorMessage(error instanceof Error ? error.message : '참여자 강퇴에 실패했습니다.')
        } finally {
          setIsBusy(false)
        }
      })()
    },
    [isHost, isWaitingRoom, refreshRoom, roomCode, userUuid],
  )

  const leaveRoom = useCallback(() => {
    void (async () => {
      startActionRequest()

      if (!roomCode) {
        resetRoomSession({ clearResult: true })
        setCurrentStep('booth')
        return
      }
      linkRoomCodeHandledRef.current = roomCode

      // 명시적 나가기 — 현재 step에 따른 이탈 이벤트 1종 발사.
      const status = roomState?.status
      if (status === 'WAITING') {
        logEvent('room_lobby_abandoned', {
          contentType: 'flipbook',
          roomId: roomCode,
          metadata: {
            content_type: 'flipbook',
            room_id: roomCode,
            participant_count: roomState ? getRoomParticipantCount(roomState) : undefined,
          },
        })
      } else if (status === 'PLAYING' || status === 'FINALIZING') {
        const assignmentKey = assignment ? getAssignmentKey(assignment) : null
        const submitted = assignmentKey
          ? submittedAssignmentKeys.has(assignmentKey) ||
            isFlipbookAssignmentSubmitted(assignment)
          : false
        if (!submitted) {
          logEvent('creation_abandoned', {
            contentType: 'flipbook',
            roomId: roomCode,
            metadata: {
              content_type: 'flipbook',
              funnel_name: 'flipbook_room_creation',
              step_name: 'drawing',
            },
          })
        }
      } else if (status === 'FINISHED') {
        logEvent('result_share_abandoned', {
          contentType: 'flipbook',
          roomId: roomCode,
          metadata: {
            content_type: 'flipbook',
          },
        })
      }

      if (status === 'WAITING') {
        try {
          await deleteFlipbookRoomParticipantMe(roomCode)
        } catch {
          // 이미 방이 닫혔거나 참여 상태가 아닐 수 있으므로 화면 이탈은 계속 진행한다.
        }
      }

      resetRoomSession({ clearResult: true })
      setCurrentStep('booth')
    })()
  }, [
    assignment,
    resetRoomSession,
    roomCode,
    roomState,
    setCurrentStep,
    startActionRequest,
    submittedAssignmentKeys,
  ])

  const closeRoom = useCallback(() => {
    if (!roomCode || !isHost || roomState?.status !== 'FINISHED') return

    void (async () => {
      const requestSequence = startActionRequest()
      setIsBusy(true)
      setErrorMessage(null)

      try {
        await postFlipbookRoomClose(roomCode)
        if (!isCurrentActionRequest(requestSequence)) return

        linkRoomCodeHandledRef.current = roomCode
        resetRoomSession({ clearResult: true })
        setCurrentStep('booth')
        setErrorMessage('플립북 방을 종료했습니다.')
      } catch (error) {
        if (!isCurrentActionRequest(requestSequence)) return

        setErrorMessage(error instanceof Error ? error.message : '방 종료에 실패했습니다.')
      } finally {
        if (isCurrentActionRequest(requestSequence)) {
          setIsBusy(false)
        }
      }
    })()
  }, [
    isCurrentActionRequest,
    isHost,
    resetRoomSession,
    roomCode,
    roomState?.status,
    setCurrentStep,
    startActionRequest,
  ])

  const selectStep = useCallback(
    (step: FlipbookStep) => {
      if (step === 'booth') {
        if (roomCode) {
          linkRoomCodeHandledRef.current = roomCode
        }
        startActionRequest()
        resetRoomSession({ clearResult: true })
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
      resetDrawingRound,
      resetRoomSession,
      resultPlayback,
      roomCode,
      setCurrentStep,
      startActionRequest,
    ],
  )

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (isDummyResultPreviewRoute()) return
      const targetRoomCode = readRouteRoomCode()
      if (!targetRoomCode || cancelled) return
      if (!targetRoomCode) return

      setRoomCodeDraft(targetRoomCode)

      if (
        !userUuid ||
        roomCode === targetRoomCode ||
        linkRoomCodeHandledRef.current === targetRoomCode ||
        cancelled
      ) {
        return
      }

      linkRoomCodeHandledRef.current = targetRoomCode

      if (!hasConfiguredNickname(nickname)) {
        openNicknameModal('enterRoom')
        return
      }

      await hydrateRouteRoom(targetRoomCode)
    })()

    return () => {
      cancelled = true
    }
  }, [
    nickname,
    openNicknameModal,
    hydrateRouteRoom,
    readRouteRoomCode,
    roomCode,
    userUuid,
  ])

  useEffect(() => {
    void (async () => {
      if (realtime.connectionStatus !== 'connected' || !roomCode) {
        connectedRoomProgressSyncRef.current = null
        return
      }

      if (currentStep === 'booth' || connectedRoomProgressSyncRef.current === roomCode) {
        return
      }

      connectedRoomProgressSyncRef.current = roomCode
      try {
        await syncActiveRoomProgress(roomCode)
      } catch (error) {
        connectedRoomProgressSyncRef.current = null
        setErrorMessage(error instanceof Error ? error.message : '방 상태를 동기화하지 못했습니다.')
      }
    })()
  }, [currentStep, realtime.connectionStatus, roomCode, syncActiveRoomProgress])

  useEffect(() => {
    if (!roomCode || currentStep !== 'drawing' || !isRoundSubmitted) return
    if (realtime.connectionStatus === 'connected') return

    let cancelled = false
    let pollingTimer: number | null = null

    const pollSubmittedRound = async () => {
      if (cancelled) return

      try {
        const nextRoomState = await syncActiveRoomProgress(roomCode)
        if (cancelled) return

        if (nextRoomState?.status === 'PLAYING') {
          pollingTimer = window.setTimeout(
            pollSubmittedRound,
            SUBMITTED_ROUND_POLLING_INTERVAL_MS,
          )
        }
      } catch {
        if (!cancelled) {
          pollingTimer = window.setTimeout(
            pollSubmittedRound,
            SUBMITTED_ROUND_POLLING_INTERVAL_MS,
          )
        }
      }
    }

    void pollSubmittedRound()

    return () => {
      cancelled = true
      if (pollingTimer !== null) {
        window.clearTimeout(pollingTimer)
      }
    }
  }, [
    assignment,
    currentStep,
    isRoundSubmitted,
    realtime.connectionStatus,
    roomCode,
    syncActiveRoomProgress,
  ])

  useEffect(() => {
    if (isDummyResultPreview || currentStep !== 'result' || !roomCode || isResultReady) return

    let cancelled = false
    let pollingTimer: number | null = null
    const pollResult = async () => {
      if (cancelled) return

      try {
        const nextResult = await fetchResult(roomCode)
        if (!nextResult?.ready && !cancelled) {
          pollingTimer = window.setTimeout(pollResult, RESULT_POLLING_INTERVAL_MS)
        }
      } catch {
        if (!cancelled) {
          pollingTimer = window.setTimeout(pollResult, RESULT_POLLING_INTERVAL_MS)
        }
      }
    }

    void pollResult()

    return () => {
      cancelled = true
      if (pollingTimer !== null) {
        window.clearTimeout(pollingTimer)
      }
    }
  }, [currentStep, fetchResult, isDummyResultPreview, isResultReady, roomCode])

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
    timeLimitOptions,
    roundCount: perParticipantRoundCount,
    drawingRoundCount,
    activeRoundIndex,
    remainingSeconds: timer.remainingSeconds,
    currentParticipant: displayedParticipant,
    participants,
    participantCount,
    submittedCount: displayedSubmittedFrameCount,
    totalCount: displayedSubmissionTotalCount,
    maxParticipants: roomState?.maxParticipants ?? 12,
    minParticipants: roomState?.minParticipants ?? 2,
    roomStatus: roomState?.status ?? null,
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
    isAssignmentReady: assignment !== null,
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
    kickParticipant,
    leaveRoom,
    closeRoom,
    canLeaveRoom: isWaitingRoom && Boolean(roomCode),
    canCloseRoom: isHost && roomState?.status === 'FINISHED' && Boolean(roomCode),
    setIsGifPlaying: resultPlayback.setIsGifPlaying,
    showResultFrame: resultPlayback.showResultFrame,
    showPreviousResultFrame: resultPlayback.showPreviousResultFrame,
    showNextResultFrame: resultPlayback.showNextResultFrame,
    selectResult,
  }
}
