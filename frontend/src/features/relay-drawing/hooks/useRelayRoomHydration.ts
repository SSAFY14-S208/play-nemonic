'use client'

import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { getRelayRoom } from '@/shared/apis'
import { useUserStore } from '@/shared/stores'

import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import { relayToast } from '@/features/relay-drawing/utils'
import { markRoomDismissed } from './relayRoomDismissal'
import {
  getBlockedReasonForRoom,
  getBlockedRoomMessage,
  getHydrationErrorMessage,
  joinWaitingRoom,
  shouldRedirectBlockedViewer,
  syncFinishedRoomResults,
  syncPlayingRoomState,
} from './relayRoomHydrationUtils'
import { useRelayRoomLeaveCleanup } from './useRelayRoomLeaveCleanup'

interface UseRelayRoomHydrationReturn {
  isFetching: boolean
  hydrationError: string | null
  isHydrating: boolean
  isViewerParticipant: boolean
}

export function useRelayRoomHydration(
  roomCode: string | null,
): UseRelayRoomHydrationReturn {
  const router = useRouter()
  const currentUserUuid = useUserStore((state) => state.userUuid)
  const storeRoomCode = useRelayDrawingStore((state) => state.roomCode)
  const participants = useRelayDrawingStore((state) => state.participants)
  const hydrateRoomState = useRelayDrawingStore((state) => state.hydrateRoomState)
  const isStoreSyncedToUrl = storeRoomCode === roomCode
  const [isFetching, setIsFetching] = useState(!isStoreSyncedToUrl)
  const [hydrationError, setHydrationError] = useState<string | null>(null)

  useRelayRoomLeaveCleanup(roomCode)

  useEffect(() => {
    if (!roomCode) return

    let cancelled = false

    void (async () => {
      setIsFetching(true)
      setHydrationError(null)

      try {
        const room = await getRelayRoom(roomCode)
        if (cancelled) return

        if (shouldRedirectBlockedViewer(room)) {
          const blockedReason = getBlockedReasonForRoom(room)
          markRoomDismissed(roomCode)
          relayToast(
            blockedReason
              ? getBlockedRoomMessage(blockedReason)
              : '입장할 수 없는 방입니다.',
          )
          router.replace('/relay-drawing')
          return
        }

        hydrateRoomState(room)

        if (
          room.status === 'WAITING' &&
          room.viewer.canJoin &&
          !room.viewer.participant
        ) {
          const joinedRoom = await joinWaitingRoom(roomCode)
          if (cancelled) return
          hydrateRoomState(joinedRoom)
        }

        syncPlayingRoomState(room)

        if (room.status === 'FINISHED') {
          await syncFinishedRoomResults(roomCode)
        }
      } catch (caughtError) {
        if (!cancelled) {
          setHydrationError(getHydrationErrorMessage(caughtError))
        }
      } finally {
        if (!cancelled) setIsFetching(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [roomCode, hydrateRoomState, router])

  const isViewerParticipant =
    currentUserUuid !== null &&
    storeRoomCode === roomCode &&
    participants.some(
      (participant) => participant.userUuid === currentUserUuid,
    )
  const isHydrating = isFetching && !isStoreSyncedToUrl

  return { isFetching, hydrationError, isHydrating, isViewerParticipant }
}
