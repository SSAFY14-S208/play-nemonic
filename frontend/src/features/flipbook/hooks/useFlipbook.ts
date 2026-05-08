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
  FlipbookRealtimeEvent,
  FlipbookResultItemResponse,
  FlipbookRoomParticipantResponse,
  FlipbookRoomStateResponse,
} from '@/shared/types'
import { renderLinesToRasterCanvas } from '@/shared/utils'
import {
  FLIPBOOK_BACKGROUND_COLOR,
  FLIPBOOK_BOARD_SIZE,
  FLIPBOOK_COLORS,
  FLIPBOOK_TIME_LIMITS_SECONDS,
  type FlipbookParticipant,
  type FlipbookStep,
  type FlipbookTimeLimitSeconds,
} from '../constants'
import type { FlipbookFrame } from '../types'
import { createLocalFlipbookParticipant } from '../utils'
import { useFlipbookRealtimeConnection } from './useFlipbookRealtimeConnection'
import { useFlipbookResultPlayback } from './useFlipbookResultPlayback'
import { useFlipbookTimer } from './useFlipbookTimer'

const FLIPBOOK_FILE_CONTENT_TYPE = 'image/png'
const FLIPBOOK_FILE_PURPOSE = 'FLIPBOOK'
const RESULT_POLLING_INTERVAL_MS = 1500
const ASSIGNMENT_RETRY_DELAYS_MS = [1000, 2000, 3000, 5000]

type FlipbookNicknamePendingAction = 'createRoom' | 'enterRoom'

function isFlipbookTimeLimitSeconds(seconds: number): seconds is FlipbookTimeLimitSeconds {
  return FLIPBOOK_TIME_LIMITS_SECONDS.includes(seconds as FlipbookTimeLimitSeconds)
}

function toFlipbookTimeLimitSeconds(seconds: number): FlipbookTimeLimitSeconds {
  return isFlipbookTimeLimitSeconds(seconds) ? seconds : FLIPBOOK_TIME_LIMITS_SECONDS[1]
}

function getMinimumRoundCount(participantCount: number) {
  return Math.max(1, Math.ceil(8 / Math.max(1, participantCount)))
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
    imageDataUrl: imageUrl,
  }
}

function createPreviousFrameLinesFromAssignment(
  assignment: FlipbookAssignmentResponse,
): DrawingLine[] {
  if (!assignment.hint?.url || assignment.hint.empty) return []

  return [
    createImageLineFromUrl(
      `flipbook-hint-${assignment.hint.flipbookIndex}-${assignment.hint.frameIndex}-${assignment.hint.round}`,
      assignment.hint.url,
    ),
  ]
}

function getResultFrames(result: FlipbookResultItemResponse | null): FlipbookFrame[] {
  if (!result) return []

  return result.frames.map((frame) => ({
    id: `${result.flipbookIndex}-${frame.frameIndex}`,
    index: frame.frameIndex,
    drawnByUserUuid: frame.drawnByUserUuid,
    drawnBy: frame.drawnByNickname,
    participantAvatar: '🙂',
    lines: [],
    imageUrl: frame.imageUrl,
  }))
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

export function useFlipbook() {
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
  const [currentStep, setCurrentStep] = useState<FlipbookStep>('booth')
  const [roomCode, setRoomCode] = useState<string | null>(null)
  const [roomCodeDraft, setRoomCodeDraft] = useState('')
  const [roomState, setRoomState] = useState<FlipbookRoomStateResponse | null>(null)
  const [assignment, setAssignment] = useState<FlipbookAssignmentResponse | null>(null)
  const [previousFrameLines, setPreviousFrameLines] = useState<DrawingLine[]>([])
  const [selectedTimeLimitSeconds, setSelectedTimeLimitSeconds] =
    useState<FlipbookTimeLimitSeconds>(FLIPBOOK_TIME_LIMITS_SECONDS[1])
  const [roundCount, setRoundCount] = useState(1)
  const [resultItems, setResultItems] = useState<FlipbookResultItemResponse[]>([])
  const [activeResultIndex, setActiveResultIndex] = useState(0)
  const [isResultReady, setIsResultReady] = useState(false)
  const [resultCount, setResultCount] = useState(0)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isBusy, setIsBusy] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [nicknameModalOpen, setNicknameModalOpen] = useState(false)
  const isCompletingRoundRef = useRef(false)
  const linkRoomCodeHandledRef = useRef(false)
  const assignmentRequestSequenceRef = useRef(0)
  const pendingNicknameActionRef = useRef<FlipbookNicknamePendingAction | null>(null)

  const participantCount = roomState?.participantCount ?? 1
  const minimumRoundCount = getMinimumRoundCount(participantCount)
  const participants = useMemo(
    () =>
      roomState?.participants.map((participant) => toParticipant(participant, userUuid)) ?? [
        currentParticipant,
      ],
    [currentParticipant, roomState?.participants, userUuid],
  )
  const displayedParticipant =
    participants.find((participant) => participant.userUuid === userUuid) ?? currentParticipant
  const activeRoundIndex = Math.max(0, (assignment?.currentRound ?? roomState?.currentRound ?? 1) - 1)
  const canStartGame = roomState?.viewer.canStart === true
  const isHost = roomState?.viewer.host === true
  const isRoundSubmitted = assignment?.assignmentStatus !== undefined && assignment.assignmentStatus !== 'PENDING'
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

  const refreshRoom = useCallback(
    async (targetRoomCode = roomCode) => {
      if (!targetRoomCode) return null
      const nextRoomState = await getFlipbookRoom(targetRoomCode)
      setRoomState(nextRoomState)
      setSelectedTimeLimitSeconds(toFlipbookTimeLimitSeconds(nextRoomState.timeLimitSeconds))
      setRoundCount(nextRoomState.totalRounds ?? getMinimumRoundCount(nextRoomState.participantCount))

      if (nextRoomState.status === 'WAITING') {
        setCurrentStep('lobby')
      } else if (nextRoomState.status === 'PLAYING') {
        setCurrentStep('drawing')
      } else if (nextRoomState.status === 'FINISHED') {
        setCurrentStep('result')
      }

      return nextRoomState
    },
    [roomCode],
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
          setRoundCount(nextAssignment.totalRounds)
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
    [resetDrawingRound, roomCode],
  )

  const fetchResult = useCallback(
    async (targetRoomCode = roomCode) => {
      if (!targetRoomCode) return null
      const nextResult = await getFlipbookRoomResult(targetRoomCode)
      setResultCount(nextResult.resultCount)

      if (nextResult.ready) {
        setResultItems(nextResult.results)
        setActiveResultIndex((currentIndex) =>
          Math.min(currentIndex, Math.max(0, nextResult.results.length - 1)),
        )
        setIsResultReady(true)
        setCurrentStep('result')
        resultPlayback.resetResultFrameIndex()
      }

      return nextResult
    },
    [resultPlayback, roomCode],
  )

  const refreshPlayingRound = useCallback(
    async (targetRoomCode: string, expectedRound?: number) => {
      const nextRoomState = await refreshRoom(targetRoomCode)

      if (nextRoomState?.status === 'FINISHED') {
        clearDrawingRound()
        setCurrentStep('result')
        await fetchResult(targetRoomCode)
        return
      }

      if (nextRoomState?.status === 'PLAYING') {
        await fetchAssignment(targetRoomCode, expectedRound ?? nextRoomState.currentRound ?? undefined)
        setCurrentStep('drawing')
      }
    },
    [clearDrawingRound, fetchAssignment, fetchResult, refreshRoom],
  )

  const handleCompletedRounds = useCallback(
    async (targetRoomCode: string) => {
      await refreshRoom(targetRoomCode)
      clearDrawingRound()
      setCurrentStep('result')
      await fetchResult(targetRoomCode)
    },
    [clearDrawingRound, fetchResult, refreshRoom],
  )

  const handleSubmittedFrameProgress = useCallback(
    async (targetRoomCode: string, submittedFrame: Partial<FlipbookFrameSubmitResponse>) => {
      const nextRoomState = await refreshRoom(targetRoomCode)

      if (
        submittedFrame.allRoundsCompleted ||
        submittedFrame.roomStatus === 'FINISHED' ||
        nextRoomState?.status === 'FINISHED'
      ) {
        await handleCompletedRounds(targetRoomCode)
        return
      }

      if (submittedFrame.advanced || submittedFrame.nextRound) {
        await fetchAssignment(targetRoomCode, submittedFrame.nextRound ?? undefined)
        setCurrentStep('drawing')
      }
    },
    [fetchAssignment, handleCompletedRounds, refreshRoom],
  )

  const handleRealtimeEvent = useCallback(
    (event: FlipbookRealtimeEvent) => {
      void (async () => {
        if (event.type === 'PARTICIPANT_CONNECTED' || event.type === 'PARTICIPANT_DISCONNECTED') {
          await refreshRoom(event.roomCode)
          return
        }

        if (event.type === 'SETTINGS_CHANGED') {
          await refreshRoom(event.roomCode)
          return
        }

        if (event.type === 'GAME_STARTED') {
          await refreshPlayingRound(event.roomCode)
          return
        }

        if (event.type === 'ROUND_STARTED') {
          const roundStartedData = event.data as { round?: number }
          await refreshPlayingRound(event.roomCode, roundStartedData.round)
          return
        }

        if (event.type === 'FRAME_SUBMITTED') {
          const submittedFrame = event.data as Partial<FlipbookFrameSubmitResponse>
          await handleSubmittedFrameProgress(event.roomCode, submittedFrame)
          return
        }

        if (event.type === 'FRAME_AUTO_SUBMITTED') {
          const autoSubmittedFrame = event.data as {
            userUuid?: string
            round?: number
            assignmentStatus?: FlipbookAssignmentResponse['assignmentStatus']
          }
          if (autoSubmittedFrame.userUuid === userUuid) {
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
          await refreshRoom(event.roomCode)
          return
        }

        if (event.type === 'ALL_ROUNDS_COMPLETED') {
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
          setErrorMessage('방이 종료되었습니다.')
          return
        }

        if (event.type === 'KICKED_FROM_ROOM') {
          setCurrentStep('booth')
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
    [handleCompletedRounds, handleSubmittedFrameProgress, refreshPlayingRound, refreshRoom, userUuid],
  )

  const realtime = useFlipbookRealtimeConnection({
    enabled: currentStep !== 'booth' && Boolean(roomCode),
    roomCode,
    onEvent: handleRealtimeEvent,
  })

  const timer = useFlipbookTimer({
    activeRoundIndex,
    currentStep,
    deadlineAt: assignment?.roundDeadlineAt ?? roomState?.roundDeadlineAt ?? null,
    initialRemainingSeconds: assignment?.remainingSeconds ?? null,
    selectedTimeLimitSeconds,
    onTimeExpired: () => {
      void completeRound()
    },
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
      await refreshRoom(createdRoom.roomCode)
      setCurrentStep('lobby')
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
  }, [isBusy, openNicknameModal, refreshRoom, userUuid])

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
      await refreshRoom(joinedRoom.roomId)
      setCurrentStep('lobby')
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
      setRoundCount(startedRoom.totalRounds ?? minimumRoundCount)
      await fetchAssignment(roomCode, startedRoom.currentRound ?? undefined)
      setCurrentStep('drawing')
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '게임 시작에 실패했습니다.')
    } finally {
      setIsBusy(false)
    }
  }, [canStartGame, fetchAssignment, isBusy, minimumRoundCount, roomCode])

  const completeRound = useCallback(async () => {
    if (!roomCode || !assignment || isCompletingRoundRef.current) return
    if (assignment.assignmentStatus !== 'PENDING') return

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
      await handleSubmittedFrameProgress(roomCode, submittedFrame)
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '프레임 제출에 실패했습니다.')
      await refreshRoom(roomCode)
    } finally {
      isCompletingRoundRef.current = false
      setIsSubmitting(false)
    }
  }, [assignment, drawingBoard.lines, handleSubmittedFrameProgress, refreshRoom, roomCode, uploadFrame])

  const selectTimeLimit = useCallback(
    (timeLimitSeconds: FlipbookTimeLimitSeconds) => {
      if (!roomCode || !isHost) return

      setSelectedTimeLimitSeconds(timeLimitSeconds)
      void (async () => {
        try {
          const updatedRoom = await patchFlipbookRoomSettings(roomCode, timeLimitSeconds)
          setRoomState(updatedRoom)
        } catch (error) {
          setErrorMessage(error instanceof Error ? error.message : '제한 시간 변경에 실패했습니다.')
        }
      })()
    },
    [isHost, roomCode],
  )

  const increaseRoundCount = useCallback(() => {
    setRoundCount((currentRoundCount) => currentRoundCount + 1)
  }, [])

  const decreaseRoundCount = useCallback(() => {
    setRoundCount((currentRoundCount) => Math.max(minimumRoundCount, currentRoundCount - 1))
  }, [minimumRoundCount])

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
      clearDrawingRound()
      setCurrentStep('booth')
    })()
  }, [clearDrawingRound, roomCode, roomState?.status])

  const selectStep = useCallback(
    (step: FlipbookStep) => {
      if (step === 'booth') {
        setRoomCode(null)
        setRoomState(null)
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
    [clearDrawingRound, resetDrawingRound, resultPlayback],
  )

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (typeof window === 'undefined') return
      const queryRoomCode = new URLSearchParams(window.location.search).get('roomCode')
      if (!queryRoomCode || cancelled) return
      const targetRoomCode = queryRoomCode.trim().toUpperCase()
      if (!targetRoomCode) return

      setRoomCodeDraft(targetRoomCode)

      if (!userUuid || currentStep !== 'booth' || linkRoomCodeHandledRef.current || cancelled) return

      linkRoomCodeHandledRef.current = true

      if (!hasConfiguredNickname(nickname)) {
        openNicknameModal('enterRoom')
        return
      }

      await performEnterRoom(targetRoomCode)
    })()

    return () => {
      cancelled = true
    }
  }, [currentStep, nickname, openNicknameModal, performEnterRoom, userUuid])

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
    roundCount,
    minimumRoundCount,
    activeRoundIndex,
    remainingSeconds: timer.remainingSeconds,
    currentParticipant: displayedParticipant,
    participants,
    participantCount,
    maxParticipants: roomState?.maxParticipants ?? 12,
    previousFrameLines,
    resultItems,
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
    increaseRoundCount,
    decreaseRoundCount,
    leaveRoom,
    setIsGifPlaying: resultPlayback.setIsGifPlaying,
    showResultFrame: resultPlayback.showResultFrame,
    showPreviousResultFrame: resultPlayback.showPreviousResultFrame,
    showNextResultFrame: resultPlayback.showNextResultFrame,
    selectResult,
  }
}
