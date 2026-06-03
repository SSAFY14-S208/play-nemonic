'use client'

import { useRouter } from 'next/navigation'

import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import { relayToast } from '@/features/relay-drawing/utils'
import { hasRoomDismissed, markRoomDismissed } from './relayRoomDismissal'
import type { RelayEventHandlers } from './useRelaySocket'

export function useRelayDismissalSocketHandlers(
  roomCode: string | null,
): RelayEventHandlers {
  const router = useRouter()
  const setDismissalReason = useRelayDrawingStore(
    (state) => state.setDismissalReason,
  )
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom)

  return {
    ROOM_CLOSED: () => {
      markRoomDismissed(roomCode)
      setDismissalReason('ROOM_CLOSED')
    },
    KICKED_FROM_ROOM: () => {
      if (hasRoomDismissed(roomCode)) return

      markRoomDismissed(roomCode)
      relayToast.error('방장에 의해 방에서 내보내졌습니다.')
      clearRoom()
      router.push('/relay-drawing')
    },
    DUPLICATE_SESSION_CLOSED: () => {
      markRoomDismissed(roomCode)
      setDismissalReason('DUPLICATE_SESSION')
    },
  }
}
