'use client'

import { useMemo } from 'react'

import type {
  FlipbookAssignmentResponse,
  FlipbookRoomStateResponse,
} from '@/shared/types'
import type { FlipbookParticipant } from '../types'
import {
  getAssignmentKey,
  getRoomParticipantCount,
  getServerRoundCount,
  isFlipbookAssignmentSubmitted,
  toFlipbookParticipant,
} from '../utils'

interface UseFlipbookRoomDerivedStateParams {
  assignment: FlipbookAssignmentResponse | null
  currentParticipant: FlipbookParticipant
  roomCode: string | null
  roomState: FlipbookRoomStateResponse | null
  roundCount: number | null
  submittedAssignmentKeys: Set<string>
  submittedFrameCount: number
  submissionTotalCount: number
  userUuid: string | null
}

export function useFlipbookRoomDerivedState({
  assignment,
  currentParticipant,
  roomCode,
  roomState,
  roundCount,
  submittedAssignmentKeys,
  submittedFrameCount,
  submissionTotalCount,
  userUuid,
}: UseFlipbookRoomDerivedStateParams) {
  const activeRoomState = roomCode !== null && roomState?.roomCode === roomCode ? roomState : null
  const participantCount = getRoomParticipantCount(activeRoomState)
  const displayedSubmissionTotalCount = submissionTotalCount
  const displayedSubmittedFrameCount = Math.min(
    submittedFrameCount,
    displayedSubmissionTotalCount,
  )
  const participants = useMemo(
    () =>
      activeRoomState?.participants.map((participant) => toFlipbookParticipant(participant)) ?? [
        currentParticipant,
      ],
    [activeRoomState?.participants, currentParticipant],
  )
  const resultOwnerNames = useMemo(
    () =>
      [...(activeRoomState?.participants ?? [])]
        .sort(
          (firstParticipant, secondParticipant) =>
            firstParticipant.joinOrder - secondParticipant.joinOrder,
        )
        .map((participant) => participant.nickname),
    [activeRoomState?.participants],
  )
  const displayedParticipant =
    participants.find((participant) => participant.userUuid === userUuid) ?? currentParticipant
  const perParticipantRoundCount = getServerRoundCount({
    roomState: activeRoomState,
    fallback: roundCount ?? assignment?.totalRounds ?? null,
  })
  const drawingRoundCount = perParticipantRoundCount
  const activeRoundIndex = Math.max(
    0,
    (assignment?.currentRound ?? activeRoomState?.currentRound ?? 1) - 1,
  )
  const isWaitingRoom = activeRoomState?.status === 'WAITING'
  const isRoomParticipant =
    userUuid !== null &&
    activeRoomState?.viewer.participant === true &&
    activeRoomState.participants.some((participant) => participant.userUuid === userUuid)
  const canStartGame = isWaitingRoom && activeRoomState?.viewer.canStart === true
  const isHost = activeRoomState?.viewer.host === true
  const activeAssignmentKey = assignment ? getAssignmentKey(assignment) : null
  const isServerAssignmentSubmitted = isFlipbookAssignmentSubmitted(assignment)
  const isRoundSubmitted =
    activeAssignmentKey !== null &&
    (submittedAssignmentKeys.has(activeAssignmentKey) || isServerAssignmentSubmitted)

  return {
    activeAssignmentKey,
    activeRoomState,
    activeRoundIndex,
    canStartGame,
    displayedParticipant,
    displayedSubmittedFrameCount,
    displayedSubmissionTotalCount,
    drawingRoundCount,
    isHost,
    isRoomParticipant,
    isRoundSubmitted,
    isServerAssignmentSubmitted,
    isWaitingRoom,
    participantCount,
    participants,
    perParticipantRoundCount,
    resultOwnerNames,
  }
}

export type FlipbookRoomDerivedState = ReturnType<typeof useFlipbookRoomDerivedState>
