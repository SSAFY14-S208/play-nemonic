'use client'

import { useEffect } from 'react'

import { deleteRelayRoomParticipantMe } from '@/shared/apis'

import {
  hasRoomDismissed,
  resetRoomDismissed,
} from './relayRoomDismissal'

export function useRelayRoomLeaveCleanup(roomCode: string | null) {
  useEffect(() => {
    if (!roomCode) return

    resetRoomDismissed(roomCode)

    return () => {
      if (!hasRoomDismissed(roomCode)) {
        void deleteRelayRoomParticipantMe(roomCode).catch(() => {})
      }
      resetRoomDismissed(roomCode)
    }
  }, [roomCode])
}
