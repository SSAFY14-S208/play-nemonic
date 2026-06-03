'use client'

import { useCallback, useEffect } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ApiError, postRelayRoomStart } from '@/shared/apis'
import { usePhoneLauncherStore, useUserStore } from '@/shared/stores'

import { useRelayAbandonmentTracking, useRelayRoom } from '@/features/relay-drawing/hooks'
import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import { relayToast } from '@/features/relay-drawing/utils'

export function useRelayRoomViewState() {
  const router = useRouter()
  const { roomCode } = useParams<{ roomCode: string }>()
  const { isHydrating, hydrationError } = useRelayRoom(roomCode ?? null)
  useRelayAbandonmentTracking()

  const roomStatus = useRelayDrawingStore((state) => state.roomStatus)
  const dismissalReason = useRelayDrawingStore((state) => state.dismissalReason)
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom)
  const gameStartPhase = useRelayDrawingStore((state) => state.gameStartPhase)
  const setGameStartPhase = useRelayDrawingStore(
    (state) => state.setGameStartPhase,
  )
  const hostUserUuid = useRelayDrawingStore((state) => state.hostUserUuid)
  const userUuid = useUserStore((state) => state.userUuid)
  const setLauncherHidden = usePhoneLauncherStore(
    (state) => state.setLauncherHidden,
  )

  useEffect(() => {
    setLauncherHidden(true)
    return () => setLauncherHidden(false)
  }, [setLauncherHidden])

  const isHost = userUuid !== null && userUuid === hostUserUuid

  const handleDismissalConfirm = useCallback(() => {
    clearRoom()
    router.push('/relay-drawing')
  }, [clearRoom, router])

  const handleGameStartImageShown = useCallback(() => {
    if (!isHost || !roomCode) return

    void (async () => {
      try {
        await postRelayRoomStart(roomCode)
      } catch (caughtError) {
        useRelayDrawingStore.getState().setGameStartPhase('idle')
        relayToast.error(
          caughtError instanceof ApiError
            ? caughtError.message
            : '릴레이 드로잉을 시작하지 못했습니다.',
        )
      }
    })()
  }, [isHost, roomCode])

  useEffect(() => {
    if (roomStatus !== 'PLAYING' || gameStartPhase !== 'animating') return

    const timer = setTimeout(() => {
      setGameStartPhase('idle')
    }, 1500)

    return () => clearTimeout(timer)
  }, [roomStatus, gameStartPhase, setGameStartPhase])

  return {
    isHydrating,
    hydrationError,
    roomStatus,
    dismissalReason,
    gameStartPhase,
    showWorldHomeLink: roomStatus === 'FINISHED',
    showFloatingControls:
      roomStatus === 'FINISHED' ||
      roomStatus === 'FINALIZING' ||
      (roomStatus === 'PLAYING' && gameStartPhase === 'idle'),
    handleDismissalConfirm,
    handleGameStartImageShown,
    handleGoRelayDrawingHome: () => router.push('/relay-drawing'),
  }
}

export type RelayRoomViewState = ReturnType<typeof useRelayRoomViewState>
