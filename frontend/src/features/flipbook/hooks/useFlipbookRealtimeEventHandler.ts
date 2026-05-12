'use client'

import { useCallback, type Dispatch, type SetStateAction } from 'react'
import type {
  DrawingLine,
  FlipbookAssignmentResponse,
  FlipbookFrameSubmitResponse,
  FlipbookRealtimeEvent,
  FlipbookRoomStateResponse,
} from '@/shared/types'
import type { FlipbookStep } from '../types'
import { getAssignmentKey } from '../utils'

interface UseFlipbookRealtimeEventHandlerOptions {
  assignment: FlipbookAssignmentResponse | null
  submittedAssignmentKeys: Set<string>
  userUuid: string | null
  clearRoundTransitionFallbackTimer: () => void
  clearDrawingRound: () => void
  fetchResult: (roomCode: string) => Promise<unknown>
  handleCompletedRounds: (roomCode: string) => Promise<void>
  handleSubmittedFrameProgress: (roomCode: string) => Promise<FlipbookRoomStateResponse | null>
  refreshPlayingRound: (roomCode: string, expectedRound?: number) => Promise<void>
  refreshRoom: (
    roomCode: string,
    options?: {
      syncStep?: boolean
    },
  ) => Promise<FlipbookRoomStateResponse | null>
  scheduleRoundTransitionFallback: (
    roomCode: string,
    submittedFrame: Partial<FlipbookFrameSubmitResponse>,
  ) => void
  setAssignment: Dispatch<SetStateAction<FlipbookAssignmentResponse | null>>
  setCurrentStep: (step: FlipbookStep, options?: { roomCode?: string | null }) => void
  setErrorMessage: Dispatch<SetStateAction<string | null>>
  setIsSubmitting: Dispatch<SetStateAction<boolean>>
  setPreviousFrameLines: Dispatch<SetStateAction<DrawingLine[]>>
  setRoomCode: Dispatch<SetStateAction<string | null>>
  setRoomState: Dispatch<SetStateAction<FlipbookRoomStateResponse | null>>
  setRoundCount: Dispatch<SetStateAction<number | null>>
  setStartedParticipantCount: Dispatch<SetStateAction<number | null>>
  setSubmittedAssignmentKeys: Dispatch<SetStateAction<Set<string>>>
  setTimeUpSubmitRequest: Dispatch<
    SetStateAction<{
      roomCode: string
      round: number | null
      occurredAt: string
    } | null>
  >
}

export function useFlipbookRealtimeEventHandler({
  assignment,
  submittedAssignmentKeys,
  userUuid,
  clearRoundTransitionFallbackTimer,
  clearDrawingRound,
  fetchResult,
  handleCompletedRounds,
  handleSubmittedFrameProgress,
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
  setStartedParticipantCount,
  setSubmittedAssignmentKeys,
  setTimeUpSubmitRequest,
}: UseFlipbookRealtimeEventHandlerOptions) {
  const resetRoomToBooth = useCallback(
    (message: string) => {
      setCurrentStep('booth')
      setRoomState(null)
      setRoomCode(null)
      setRoundCount(null)
      setStartedParticipantCount(null)
      setSubmittedAssignmentKeys(new Set())
      setPreviousFrameLines([])
      clearDrawingRound()
      clearRoundTransitionFallbackTimer()
      setErrorMessage(message)
    },
    [
      clearDrawingRound,
      clearRoundTransitionFallbackTimer,
      setCurrentStep,
      setErrorMessage,
      setPreviousFrameLines,
      setRoomCode,
      setRoomState,
      setRoundCount,
      setStartedParticipantCount,
      setSubmittedAssignmentKeys,
    ],
  )

  return useCallback(
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

        if (event.type === 'RESULT_CREATED') {
          clearRoundTransitionFallbackTimer()
          setTimeUpSubmitRequest(null)
          setIsSubmitting(false)
          await fetchResult(event.roomCode)
          return
        }

        if (event.type === 'PARTICIPANT_DROPPED') {
          await refreshRoom(event.roomCode, { syncStep: false })
          return
        }

        if (
          event.type === 'PARTICIPANT_LEFT' ||
          event.type === 'HOST_CHANGED' ||
          event.type === 'PARTICIPANT_KICKED'
        ) {
          await refreshRoom(event.roomCode)
          return
        }

        if (event.type === 'ROOM_CLOSED') {
          resetRoomToBooth('방이 종료되었습니다.')
          return
        }

        if (event.type === 'KICKED_FROM_ROOM') {
          resetRoomToBooth('방에서 내보내졌습니다.')
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
      fetchResult,
      handleCompletedRounds,
      handleSubmittedFrameProgress,
      refreshPlayingRound,
      refreshRoom,
      resetRoomToBooth,
      scheduleRoundTransitionFallback,
      setAssignment,
      setErrorMessage,
      setIsSubmitting,
      setSubmittedAssignmentKeys,
      setTimeUpSubmitRequest,
      submittedAssignmentKeys,
      userUuid,
    ],
  )
}
