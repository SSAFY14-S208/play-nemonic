'use client'

import { useRouter } from 'next/navigation'
import { useUserStore } from '@/shared/stores'

import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import { relayToast } from '@/features/relay-drawing/utils'
import { hasRoomDismissed, markRoomDismissed } from './relayRoomDismissal'
import type { RelayEventHandlers } from './useRelaySocket'

export function useRelayParticipantSocketHandlers(
  roomCode: string | null,
): RelayEventHandlers {
  const router = useRouter()
  const setParticipants = useRelayDrawingStore((state) => state.setParticipants)
  const setHostUserUuid = useRelayDrawingStore((state) => state.setHostUserUuid)
  const setTimeLimitSeconds = useRelayDrawingStore(
    (state) => state.setTimeLimitSeconds,
  )
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom)
  const hydrateRoomState = useRelayDrawingStore((state) => state.hydrateRoomState)

  return {
    PARTICIPANT_CONNECTED: (event) => {
      hydrateRoomState({
        roomCode: event.data.roomCode,
        status: event.data.status,
        hostUserUuid: event.data.hostUserUuid,
        timeLimitSeconds: event.data.timeLimitSeconds,
        minParticipants: event.data.minParticipants,
        maxParticipants: event.data.maxParticipants,
        participants: event.data.participants,
      })

      const connectedUuid = event.data.changedParticipant.userUuid
      const current = useRelayDrawingStore.getState().participants
      setParticipants(
        current.map((participant) =>
          participant.userUuid === connectedUuid
            ? { ...participant, connected: true }
            : participant,
        ),
      )

      const currentUserUuid = useUserStore.getState().userUuid
      if (connectedUuid !== currentUserUuid) {
        relayToast(`${event.data.changedParticipant.nickname}님이 돌아왔습니다.`)
      }
    },
    PARTICIPANT_DISCONNECTED: (event) => {
      const current = useRelayDrawingStore.getState().participants
      setParticipants(
        current.map((participant) =>
          participant.userUuid === event.data.userUuid
            ? { ...participant, connected: false }
            : participant,
        ),
      )
    },
    PARTICIPANT_LEFT: (event) => {
      const current = useRelayDrawingStore.getState().participants
      setParticipants(
        current.filter(
          (participant) => participant.userUuid !== event.data.leftUserUuid,
        ),
      )
      relayToast(`${event.data.leftNickname}님이 방을 나갔습니다.`)
    },
    PARTICIPANT_DROPPED: (event) => {
      const current = useRelayDrawingStore.getState().participants
      setParticipants(
        current.filter(
          (participant) => participant.userUuid !== event.data.userUuid,
        ),
      )
    },
    SETTINGS_CHANGED: (event) => {
      setTimeLimitSeconds(event.data.timeLimitSeconds)
      setParticipants(event.data.participants)
    },
    HOST_CHANGED: (event) => {
      setHostUserUuid(event.data.newHostUserUuid)

      const current = useRelayDrawingStore.getState().participants
      setParticipants(
        current.map((participant) => ({
          ...participant,
          host: participant.userUuid === event.data.newHostUserUuid,
        })),
      )

      const currentUserUuid = useUserStore.getState().userUuid
      if (event.data.newHostUserUuid === currentUserUuid) {
        relayToast('방장이 되었습니다.')
      } else {
        relayToast(`${event.data.newHostNickname}님이 방장이 되었습니다.`)
      }
    },
    PARTICIPANT_KICKED: (event) => {
      const currentUserUuid = useUserStore.getState().userUuid

      if (currentUserUuid === event.data.kickedUserUuid) {
        if (hasRoomDismissed(roomCode)) return

        markRoomDismissed(roomCode)
        relayToast.error('방장에 의해 방에서 내보내졌습니다.')
        clearRoom()
        router.push('/relay-drawing')
        return
      }

      const current = useRelayDrawingStore.getState().participants
      setParticipants(
        current.filter(
          (participant) => participant.userUuid !== event.data.kickedUserUuid,
        ),
      )
      relayToast(`${event.data.kickedNickname}님이 내보내졌습니다.`)
    },
  }
}
