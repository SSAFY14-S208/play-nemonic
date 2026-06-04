'use client'

import { useEffect, type RefObject } from 'react'

import {
  getActiveFlipbookRoomCode,
  hasConfiguredNickname,
  isDummyResultPreviewRoute,
  readRouteRoomCode,
} from '../utils'

interface UseFlipbookRouteHydrationParams {
  createRoomRequestInFlightRef: RefObject<boolean>
  handledRoomCodeRef: RefObject<string | null>
  hydrateRouteRoom: (targetRoomCode: string) => Promise<void>
  nickname: string | null
  onRequireNickname: () => void
  roomCode: string | null
  setRoomCodeDraft: (roomCodeDraft: string) => void
  userUuid: string | null
}

export function useFlipbookRouteHydration({
  createRoomRequestInFlightRef,
  handledRoomCodeRef,
  hydrateRouteRoom,
  nickname,
  onRequireNickname,
  roomCode,
  setRoomCodeDraft,
  userUuid,
}: UseFlipbookRouteHydrationParams) {
  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (isDummyResultPreviewRoute()) return
      const targetRoomCode = readRouteRoomCode()
      if (!targetRoomCode || cancelled) return
      const activeRoomCode = getActiveFlipbookRoomCode()
      if (createRoomRequestInFlightRef.current) return
      if (activeRoomCode && activeRoomCode !== targetRoomCode) return

      setRoomCodeDraft(targetRoomCode)

      if (
        !userUuid ||
        roomCode === targetRoomCode ||
        handledRoomCodeRef.current === targetRoomCode ||
        cancelled
      ) {
        return
      }

      handledRoomCodeRef.current = targetRoomCode

      if (!hasConfiguredNickname(nickname)) {
        onRequireNickname()
        return
      }

      await hydrateRouteRoom(targetRoomCode)
    })()

    return () => {
      cancelled = true
    }
  }, [
    createRoomRequestInFlightRef,
    handledRoomCodeRef,
    hydrateRouteRoom,
    nickname,
    onRequireNickname,
    roomCode,
    setRoomCodeDraft,
    userUuid,
  ])
}
