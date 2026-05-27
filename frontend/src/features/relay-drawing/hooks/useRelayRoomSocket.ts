'use client'

import { useEffect } from 'react'

import { type RelaySocketStatus } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'

import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import { useRelayDismissalSocketHandlers } from './useRelayDismissalSocketHandlers'
import { useRelayGameSocketHandlers } from './useRelayGameSocketHandlers'
import { useRelayParticipantSocketHandlers } from './useRelayParticipantSocketHandlers'
import { useRelaySocket } from './useRelaySocket'

interface UseRelayRoomSocketParams {
  roomCode: string | null
  enabled: boolean
}

interface UseRelayRoomSocketReturn {
  socketStatus: RelaySocketStatus
}

export function useRelayRoomSocket({
  roomCode,
  enabled,
}: UseRelayRoomSocketParams): UseRelayRoomSocketReturn {
  const setParticipants = useRelayDrawingStore((state) => state.setParticipants)
  const participantHandlers = useRelayParticipantSocketHandlers(roomCode)
  const gameHandlers = useRelayGameSocketHandlers(roomCode)
  const dismissalHandlers = useRelayDismissalSocketHandlers(roomCode)

  const { status: socketStatus } = useRelaySocket({
    roomCode,
    enabled,
    handlers: {
      ...participantHandlers,
      ...gameHandlers,
      ...dismissalHandlers,
    },
  })

  useEffect(() => {
    if (socketStatus !== 'connected') return

    const currentUserUuid = useUserStore.getState().userUuid
    if (!currentUserUuid) return

    const current = useRelayDrawingStore.getState().participants
    const currentParticipant = current.find(
      (participant) => participant.userUuid === currentUserUuid,
    )

    if (!currentParticipant || currentParticipant.connected) return

    setParticipants(
      current.map((participant) =>
        participant.userUuid === currentUserUuid
          ? { ...participant, connected: true }
          : participant,
      ),
    )
  }, [socketStatus, setParticipants])

  return { socketStatus }
}
