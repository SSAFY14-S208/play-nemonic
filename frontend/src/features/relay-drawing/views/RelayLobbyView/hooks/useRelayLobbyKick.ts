'use client'

import { useState } from 'react'

import { ApiError, postRelayRoomKick } from '@/shared/apis'

interface UseRelayLobbyKickParams {
  roomCode: string | null
  isHost: boolean
  currentUserUuid: string | null
}

export function useRelayLobbyKick({
  roomCode,
  isHost,
  currentUserUuid,
}: UseRelayLobbyKickParams) {
  const [kickingTargetUuid, setKickingTargetUuid] = useState<string | null>(null)
  const [kickError, setKickError] = useState<string | null>(null)

  const kickParticipant = (targetUserUuid: string) => {
    if (!roomCode || !isHost || kickingTargetUuid) return
    if (targetUserUuid === currentUserUuid) return

    setKickError(null)
    setKickingTargetUuid(targetUserUuid)
    void (async () => {
      try {
        await postRelayRoomKick(roomCode, targetUserUuid)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '참가자를 내보내지 못했습니다.'
        setKickError(message)
      } finally {
        setKickingTargetUuid(null)
      }
    })()
  }

  return {
    kickingTargetUuid,
    kickError,
    kickParticipant,
    clearKickError: () => setKickError(null),
  }
}
