'use client'

import { getRelayRoomResults } from '@/shared/apis'
import { completeFunnelStep } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'

import { PART_TO_ROUND_KEY } from '@/features/relay-drawing/constants'
import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import type { RelayEventHandlers } from './useRelaySocket'

export function useRelayGameSocketHandlers(
  roomCode: string | null,
): RelayEventHandlers {
  const setRoomStatus = useRelayDrawingStore((state) => state.setRoomStatus)
  const setParticipants = useRelayDrawingStore((state) => state.setParticipants)
  const setTimeLimitSeconds = useRelayDrawingStore(
    (state) => state.setTimeLimitSeconds,
  )

  return {
    GAME_STARTED: (event) => {
      completeFunnelStep('lobby', 3, {
        content_type: 'relay',
        room_id: roomCode,
      })

      useRelayDrawingStore.getState().setGameStartPhase('animating')
      setRoomStatus(event.data.status)
      setParticipants(event.data.participants)
      useRelayDrawingStore.getState().setIsSubmitting(false)
      useRelayDrawingStore
        .getState()
        .setPartDeadlineAt(event.data.partDeadlineAt)

      const roundKey = PART_TO_ROUND_KEY[event.data.currentPart]
      useRelayDrawingStore
        .getState()
        .setRoundDeadline(roundKey, event.data.partDeadlineAt)
      useRelayDrawingStore.getState().incrementPartFetchTrigger()
      useRelayDrawingStore.getState().clearSubmittedUserUuids()
    },
    PART_STARTED: (event) => {
      setTimeLimitSeconds(event.data.timeLimitSeconds)
      useRelayDrawingStore
        .getState()
        .setPartDeadlineAt(event.data.partDeadlineAt)

      const roundKey = PART_TO_ROUND_KEY[event.data.part]
      useRelayDrawingStore
        .getState()
        .setRoundDeadline(roundKey, event.data.partDeadlineAt)
      useRelayDrawingStore.getState().incrementPartFetchTrigger()
      useRelayDrawingStore.getState().clearSubmittedUserUuids()
      useRelayDrawingStore.getState().setPartTimeUp(false)
    },
    PART_TIME_UP: (event) => {
      useRelayDrawingStore.getState().setPartTimeUp(true)

      const currentUserUuid = useUserStore.getState().userUuid
      if (!currentUserUuid) return

      const isMePending = event.data.pendingSubmissions.some(
        (pending) => pending.userUuid === currentUserUuid,
      )
      if (!isMePending) return

      const roundKey = PART_TO_ROUND_KEY[event.data.part]
      if (useRelayDrawingStore.getState().roundSubmitted[roundKey]) return

      useRelayDrawingStore.getState().triggerPendingAutoSubmit()
    },
    PART_SUBMITTED: (event) => {
      useRelayDrawingStore
        .getState()
        .updateSubmissionProgress(
          event.data.submittedCount,
          event.data.totalCount,
        )
      useRelayDrawingStore
        .getState()
        .addSubmittedUserUuid(event.data.userUuid)
    },
    PART_AUTO_SUBMITTED: (event) => {
      useRelayDrawingStore
        .getState()
        .addSubmittedUserUuid(event.data.userUuid)
    },
    ALL_PARTS_COMPLETED: (event) => {
      setRoomStatus(event.data.roomStatus)
      useRelayDrawingStore.getState().setPartTimeUp(false)
    },
    RESULT_CREATED: (event) => {
      setRoomStatus(event.data.roomStatus)

      const currentRoomCode = useRelayDrawingStore.getState().roomCode
      if (!currentRoomCode) return

      void (async () => {
        try {
          const response = await getRelayRoomResults(currentRoomCode)
          useRelayDrawingStore.getState().setResults(response.results)
        } catch {
          // Result view also fetches result data, so this socket-side refresh can fail silently.
        }
      })()
    },
  }
}
