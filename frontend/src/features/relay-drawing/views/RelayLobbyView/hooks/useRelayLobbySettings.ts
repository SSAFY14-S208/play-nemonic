'use client'

import { useState, useTransition } from 'react'

import { ApiError, patchRelayRoomSettings } from '@/shared/apis'

import { useRelayDrawingStore } from '@/features/relay-drawing/stores'

interface UseRelayLobbySettingsParams {
  roomCode: string | null
  isHost: boolean
}

export function useRelayLobbySettings({ roomCode, isHost }: UseRelayLobbySettingsParams) {
  const setTimeLimitSeconds = useRelayDrawingStore((state) => state.setTimeLimitSeconds)
  const [isUpdatingSettings, startSettingsTransition] = useTransition()
  const [settingsError, setSettingsError] = useState<string | null>(null)

  const changeTimeLimit = (seconds: number) => {
    if (!roomCode || !isHost || isUpdatingSettings) return

    const previousSeconds = useRelayDrawingStore.getState().timeLimitSeconds
    if (previousSeconds === seconds) return

    setTimeLimitSeconds(seconds)
    setSettingsError(null)
    startSettingsTransition(async () => {
      try {
        await patchRelayRoomSettings(roomCode, seconds)
      } catch (caughtError) {
        setTimeLimitSeconds(previousSeconds)
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '제한 시간을 변경하지 못했습니다.'
        setSettingsError(message)
      }
    })
  }

  return {
    settingsError,
    isUpdatingSettings,
    changeTimeLimit,
    clearSettingsError: () => setSettingsError(null),
  }
}
