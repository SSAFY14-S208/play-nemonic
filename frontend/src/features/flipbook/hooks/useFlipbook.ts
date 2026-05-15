'use client'

import { useCallback, useEffect, useMemo, useRef, useState, useTransition } from 'react'
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
    defaultColor: DRAWING_COLORS[0],
    defaultStrokeWidth: DEFAULT_DRAWING_STROKE_WIDTH,
  })
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
    round: number | null
    occurredAt: string
  } | null>(null)
  const isCompletingRoundRef = useRef(false)
  const linkRoomCodeHandledRef = useRef<string | null>(null)
  const assignmentRequestSequenceRef = useRef(0)
  const roundTransitionFallbackTimerRef = useRef<number | null>(null)
  const pendingNicknameActionRef = useRef<FlipbookNicknamePendingAction | null>(null)
  const pendingTimeLimitSecondsRef = useRef<FlipbookTimeLimitSeconds | null>(null)
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

    const queryRoomCode = new URLSearchParams(window.location.search).get('roomCode')
    const normalizedRoomCode = queryRoomCode?.trim().toUpperCase() ?? ''

    return normalizedRoomCode || null
  }, [])

  const participantCount = getRoomParticipantCount(roomState)
  const participants = useMemo(
    () =>
      roomState?.participants.map((participant) => toFlipbookParticipant(participant)) ?? [
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
  const perParticipantRoundCount = getServerRoundCount({
    roomState,
    fallback: roundCount ?? assignment?.totalRounds ?? null,
  })
  const drawingRoundCount = perParticipantRoundCount
  const activeRoundIndex = Math.max(0, (assignment?.currentRound ?? roomState?.currentRound ?? 1) - 1)
  const isWaitingRoom = roomState?.status === 'WAITING'
  const canStartGame = isWaitingRoom && roomState?.viewer.canStart === true
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

          const nextAssignmentKey = getAssignmentKey(nextAssignment)
          const currentAssignmentKey = assignment ? getAssignmentKey(assignment) : null
          const isSameAssignment = currentAssignmentKey === nextAssignmentKey

          setAssignment(nextAssignment)
          setSelectedTimeLimitSeconds(toFlipbookTimeLimitSeconds(nextAssignment.timeLimitSeconds))
          setRoundCount((currentRoundCount) =>
            roomState?.totalRounds ?? currentRoundCount ?? nextAssignment.totalRounds,
          )
          if (!isSameAssignment) {
            resetDrawingRound()
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
    [assignment, resetDrawingRound, roomCode, roomState?.totalRounds],
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
        if (!resultGoalFiredRef.current) {
          resultGoalFiredRef.current = true
          reachFunnelGoal('result_viewed', {
            content_type: 'flipbook',
            room_id: targetRoomCode,
          })
        }
        setIsResultReady(true)
        setCurrentStep('result', { roomCode: targetRoomCode })
        resultPlayback.resetResultFrameIndex()
      } else {
        setIsResultReady(false)
      }

      return nextResult
    },
    [participantCount, resultPlayback, roomCode, setCurrentStep],
  )

  const refreshPlayingRound = useCallback(
    async (targetRoomCode: string, expectedRound?: number) => {
      const nextRoomState = await refreshRoom(targetRoomCode)

      if (nextRoomState?.status === 'FINALIZING' || nextRoomState?.status === 'FINISHED') {
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
    clearRoundTransitionFallbackTimer,
    clearDrawingRound,
    handleCompletedRounds,
    handleSubmittedFrameProgress,
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
    setSubmittedAssignmentKeys,
    setTimeUpSubmitRequest,
  })

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
      completeFunnelStep('nickname', 1, { content_type: 'flipbook' })
      completeFunnelStep('settings', 2, {
        content_type: 'flipbook',
        room_id: createdRoom.roomCode,
      })
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
      completeFunnelStep('nickname', 1, { content_type: 'flipbook' })
      completeFunnelStep('settings', 2, {
        content_type: 'flipbook',
        room_id: joinedRoom.roomId,
      })
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
      completeFunnelStep('drawing', 4, {
        content_type: 'flipbook',
        room_id: roomCode,
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
      if (!roomCode) {
        setCurrentStep('booth')
        return
      }

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
        const submitted = assignmentKey ? submittedAssignmentKeys.has(assignmentKey) : false
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

      setRoomCode(null)
      setRoomState(null)
      setRoundCount(null)
      setTimeLimitOptions([])
      setStartedParticipantCount(null)
      setSubmittedAssignmentKeys(new Set())
      clearRoundTransitionFallbackTimer()
      clearDrawingRound()
      setCurrentStep('booth')
    })()
  }, [
    assignment,
    clearDrawingRound,
    clearRoundTransitionFallbackTimer,
    roomCode,
    roomState,
    setCurrentStep,
    submittedAssignmentKeys,
  ])

  const closeRoom = useCallback(() => {
    if (!roomCode || !isHost || roomState?.status !== 'FINISHED') return

    void (async () => {
      setIsBusy(true)
      setErrorMessage(null)

      try {
        await postFlipbookRoomClose(roomCode)
        setRoomCode(null)
        setRoomState(null)
        setRoundCount(null)
        setTimeLimitOptions([])
        setStartedParticipantCount(null)
        setSubmittedAssignmentKeys(new Set())
        clearRoundTransitionFallbackTimer()
        clearDrawingRound()
        setResultItems([])
        setActiveResultIndex(0)
        setIsResultReady(false)
        setCurrentStep('booth')
        setErrorMessage('플립북 방을 종료했습니다.')
      } catch (error) {
        setErrorMessage(error instanceof Error ? error.message : '방 종료에 실패했습니다.')
      } finally {
        setIsBusy(false)
      }
    })()
  }, [
    clearDrawingRound,
    clearRoundTransitionFallbackTimer,
    isHost,
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
        setTimeLimitOptions([])
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
    void (async () => {
      if (realtime.connectionStatus !== 'connected' || !roomCode || currentStep === 'booth') {
        return
      }

      await refreshRoom(roomCode, { syncStep: false })
    })()
  }, [currentStep, realtime.connectionStatus, refreshRoom, roomCode])

  useEffect(() => {
    if (currentStep !== 'result' || !roomCode || isResultReady) return

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
    timeLimitOptions,
    roundCount: perParticipantRoundCount,
    drawingRoundCount,
    activeRoundIndex,
    remainingSeconds: timer.remainingSeconds,
    currentParticipant: displayedParticipant,
    participants,
    participantCount,
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
