'use client'

import { useCallback, type Dispatch, type SetStateAction } from 'react'
import type {
  DrawingLine,
  FlipbookAssignmentResponse,
  FlipbookFrameSubmitResponse,
  FlipbookRealtimeEvent,
  FlipbookWsRoundTimeUpData,
  FlipbookRoomStateResponse,
} from '@/shared/types'
import type { FlipbookStep, FlipbookTimeLimitSeconds } from '../types'
import { getAssignmentKey, toFlipbookTimeLimitSeconds } from '../utils'

function isFlipbookAssignmentSubmitted(assignment: FlipbookAssignmentResponse | null) {
  return (
    assignment?.assignmentStatus === 'SUBMITTED' ||
    assignment?.assignmentStatus === 'AUTO_SUBMITTED'
  )
}

function hasStartEligibleParticipants({
  minParticipants,
  participantCount,
  participants,
}: {
  minParticipants: number
  participantCount: number
  participants: FlipbookRoomStateResponse['participants']
}) {
  const allKnownParticipantsConnected =
    participants.length > 0 && participants.every((participant) => participant.connected)

  return (
    participantCount >= minParticipants &&
    participants.length >= minParticipants &&
    allKnownParticipantsConnected
  )
}

function isCurrentUserPendingAutoSubmission({
  assignment,
  roundTimeUpData,
  userUuid,
}: {
  assignment: FlipbookAssignmentResponse
  roundTimeUpData: FlipbookWsRoundTimeUpData
  userUuid: string | null
}) {
  if (!roundTimeUpData.pendingSubmissions) return true
  if (!userUuid) return false

  return roundTimeUpData.pendingSubmissions.some(
    (pendingSubmission) =>
      pendingSubmission.userUuid === userUuid &&
      pendingSubmission.flipbookIndex === assignment.flipbookIndex &&
      pendingSubmission.frameIndex === assignment.frameIndex,
  )
}

interface UseFlipbookRealtimeEventHandlerOptions {
  assignment: FlipbookAssignmentResponse | null
  submittedAssignmentKeys: Set<string>
  userUuid: string | null
  activeRoomCode: string | null
  clearRoundTransitionFallbackTimer: () => void
  clearDrawingRound: () => void
  fetchResult: (roomCode: string) => Promise<unknown>
  handleCompletedRounds: (roomCode: string) => Promise<void>
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
  setRoomCode: (roomCode: string | null) => void
  setRoomState: Dispatch<SetStateAction<FlipbookRoomStateResponse | null>>
  setRoundCount: Dispatch<SetStateAction<number | null>>
  setSelectedTimeLimitSeconds: Dispatch<SetStateAction<FlipbookTimeLimitSeconds>>
  setStartedParticipantCount: Dispatch<SetStateAction<number | null>>
  setSubmittedFrameCount: Dispatch<SetStateAction<number>>
  setSubmissionTotalCount: Dispatch<SetStateAction<number>>
  setSubmittedAssignmentKeys: Dispatch<SetStateAction<Set<string>>>
  setTimeUpSubmitRequest: Dispatch<
    SetStateAction<{
      roomCode: string
      round: number
      assignmentKey: string
      roundDeadlineAt: string
      occurredAt: string
    } | null>
  >
}

export function useFlipbookRealtimeEventHandler({
  assignment,
  submittedAssignmentKeys,
  userUuid,
  activeRoomCode,
  clearRoundTransitionFallbackTimer,
  clearDrawingRound,
  fetchResult,
  handleCompletedRounds,
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
}: UseFlipbookRealtimeEventHandlerOptions) {
  const resetRoomToBooth = useCallback(
    (message: string) => {
      setCurrentStep('booth')
      setRoomState(null)
      setRoomCode(null)
      setRoundCount(null)
      setStartedParticipantCount(null)
      setSubmittedFrameCount(0)
      setSubmissionTotalCount(0)
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
      setSubmittedFrameCount,
      setSubmissionTotalCount,
      setSubmittedAssignmentKeys,
    ],
  )

  return useCallback(
    (event: FlipbookRealtimeEvent) => {
      void (async () => {
        try {
          if (event.roomCode !== activeRoomCode) return

          if (event.type === 'PARTICIPANT_CONNECTED' || event.type === 'PARTICIPANT_DISCONNECTED') {
            const roomSnapshot = event.data as Partial<FlipbookRoomStateResponse>
            setRoomState((currentRoomState) => {
              if (!currentRoomState) return currentRoomState
              const nextStatus = roomSnapshot.status ?? currentRoomState.status
              const nextHostUserUuid = roomSnapshot.hostUserUuid ?? currentRoomState.hostUserUuid
              const nextMinParticipants =
                roomSnapshot.minParticipants ?? currentRoomState.minParticipants
              const nextParticipants = roomSnapshot.participants ?? currentRoomState.participants
              const nextParticipantCount =
                roomSnapshot.participantCount ??
                roomSnapshot.participants?.length ??
                currentRoomState.participantCount
              const nextViewerHost =
                roomSnapshot.viewer?.host ??
                nextHostUserUuid === currentRoomState.viewer.userUuid
              const nextViewerCanStart =
                roomSnapshot.viewer?.canStart ??
                (nextStatus === 'WAITING' &&
                  nextViewerHost &&
                  hasStartEligibleParticipants({
                    minParticipants: nextMinParticipants,
                    participantCount: nextParticipantCount,
                    participants: nextParticipants,
                  }))

              return {
                ...currentRoomState,
                status: nextStatus,
                hostUserUuid: nextHostUserUuid,
                timeLimitSeconds: roomSnapshot.timeLimitSeconds ?? currentRoomState.timeLimitSeconds,
                minParticipants: nextMinParticipants,
                maxParticipants: roomSnapshot.maxParticipants ?? currentRoomState.maxParticipants,
                participantCount: nextParticipantCount,
                currentRound: roomSnapshot.currentRound ?? currentRoomState.currentRound,
                totalRounds: roomSnapshot.totalRounds ?? currentRoomState.totalRounds,
                roundStartedAt: roomSnapshot.roundStartedAt ?? currentRoomState.roundStartedAt,
                roundDeadlineAt: roomSnapshot.roundDeadlineAt ?? currentRoomState.roundDeadlineAt,
                gameStartedAt: roomSnapshot.gameStartedAt ?? currentRoomState.gameStartedAt,
                participants: nextParticipants,
                viewer: {
                  ...currentRoomState.viewer,
                  ...roomSnapshot.viewer,
                  host: nextViewerHost,
                  canStart: nextViewerCanStart,
                },
                updatedAt: roomSnapshot.updatedAt ?? currentRoomState.updatedAt,
              }
            })
            return
          }

          if (event.type === 'SETTINGS_CHANGED') {
            const settingsChangedData = event.data as Partial<FlipbookRoomStateResponse>
            if (settingsChangedData.timeLimitSeconds !== undefined) {
              const nextTimeLimitSeconds = toFlipbookTimeLimitSeconds(
                settingsChangedData.timeLimitSeconds,
              )
              setSelectedTimeLimitSeconds(nextTimeLimitSeconds)
              setRoomState((currentRoomState) => {
                if (!currentRoomState) return currentRoomState

                return {
                  ...currentRoomState,
                  timeLimitSeconds: nextTimeLimitSeconds,
                  participants: settingsChangedData.participants ?? currentRoomState.participants,
                  participantCount:
                    settingsChangedData.participantCount ??
                    settingsChangedData.participants?.length ??
                    currentRoomState.participantCount,
                  updatedAt: settingsChangedData.updatedAt ?? currentRoomState.updatedAt,
                }
              })
            }
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
          const roundTimeUpData = event.data as FlipbookWsRoundTimeUpData
          const currentAssignmentSubmitted =
            assignment !== null &&
            (submittedAssignmentKeys.has(getAssignmentKey(assignment)) ||
              isFlipbookAssignmentSubmitted(assignment))

          if (currentAssignmentSubmitted) {
            setTimeUpSubmitRequest(null)
            setIsSubmitting(false)
            return
          }

          if (
            assignment &&
            assignment.currentRound !== roundTimeUpData.round
          ) {
            return
          }

          if (assignment && assignment.roundDeadlineAt !== roundTimeUpData.roundDeadlineAt) {
            return
          }

          if (
            !assignment ||
            !isCurrentUserPendingAutoSubmission({
              assignment,
              roundTimeUpData,
              userUuid,
            })
          ) {
            return
          }

          setIsSubmitting(true)
          setTimeUpSubmitRequest({
            roomCode: event.roomCode,
            round: roundTimeUpData.round,
            assignmentKey: getAssignmentKey(assignment),
            roundDeadlineAt: roundTimeUpData.roundDeadlineAt,
            occurredAt: event.occurredAt,
          })
          return
        }

        if (event.type === 'FRAME_SUBMITTED') {
          const submittedFrame = event.data as Partial<FlipbookFrameSubmitResponse>
          if (
            assignment &&
            submittedFrame.round === assignment.currentRound &&
            typeof submittedFrame.submittedCount === 'number' &&
            typeof submittedFrame.totalCount === 'number'
          ) {
            setSubmittedFrameCount(submittedFrame.submittedCount)
            setSubmissionTotalCount(submittedFrame.totalCount)
          }

          if (
            submittedFrame.allRoundsCompleted ||
            submittedFrame.roomStatus === 'FINALIZING' ||
            submittedFrame.roomStatus === 'FINISHED'
          ) {
            await handleCompletedRounds(event.roomCode)
            return
          }

          if (submittedFrame.advanced || submittedFrame.currentRoundCompleted) {
            scheduleRoundTransitionFallback(event.roomCode, submittedFrame)
            return
          }

          return
        }

        if (event.type === 'FRAME_AUTO_SUBMITTED') {
          const autoSubmittedFrame = event.data as {
            userUuid?: string
            round?: number
            flipbookIndex?: number
            frameIndex?: number
            assignmentStatus?: FlipbookAssignmentResponse['assignmentStatus']
          }
          const isCurrentAssignmentAutoSubmitted =
            assignment !== null &&
            autoSubmittedFrame.userUuid === userUuid &&
            autoSubmittedFrame.round === assignment.currentRound &&
            autoSubmittedFrame.flipbookIndex === assignment.flipbookIndex &&
            autoSubmittedFrame.frameIndex === assignment.frameIndex

          if (isCurrentAssignmentAutoSubmitted) {
            setSubmittedAssignmentKeys((currentKeys) => {
              const nextKeys = new Set(currentKeys)
              nextKeys.add(getAssignmentKey(assignment))
              return nextKeys
            })
            setAssignment((currentAssignment) => {
              if (
                !currentAssignment ||
                getAssignmentKey(currentAssignment) !== getAssignmentKey(assignment)
              ) {
                return currentAssignment
              }

              return {
                ...currentAssignment,
                assignmentStatus: autoSubmittedFrame.assignmentStatus ?? 'AUTO_SUBMITTED',
              }
            })

            const nextRoomState = await refreshRoom(event.roomCode, { syncStep: false })
            if (nextRoomState?.status === 'FINALIZING' || nextRoomState?.status === 'FINISHED') {
              await handleCompletedRounds(event.roomCode)
              return
            }

            if (
              nextRoomState?.status === 'PLAYING' &&
              autoSubmittedFrame.round !== undefined &&
              nextRoomState.currentRound !== null &&
              nextRoomState.currentRound > autoSubmittedFrame.round
            ) {
              await refreshPlayingRound(event.roomCode, nextRoomState.currentRound)
            }
          }
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
          setCurrentStep('result', { roomCode: event.roomCode })
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
        } catch (error) {
          setErrorMessage(
            error instanceof Error ? error.message : '방 상태를 동기화하지 못했습니다.',
          )
        }
      })()
    },
    [
      activeRoomCode,
      assignment,
      clearRoundTransitionFallbackTimer,
      fetchResult,
      handleCompletedRounds,
      refreshPlayingRound,
      refreshRoom,
      resetRoomToBooth,
      scheduleRoundTransitionFallback,
      setAssignment,
      setCurrentStep,
      setErrorMessage,
      setIsSubmitting,
      setSelectedTimeLimitSeconds,
      setRoomState,
      setSubmittedFrameCount,
      setSubmissionTotalCount,
      setSubmittedAssignmentKeys,
      setTimeUpSubmitRequest,
      submittedAssignmentKeys,
      userUuid,
    ],
  )
}
