'use client'

import { useEffect } from 'react'

import type { FlipbookConnectionStatus, FlipbookRoomStateResponse } from '@/shared/types'
import type { FlipbookStep } from '../types'

const SUBMITTED_ROUND_POLLING_INTERVAL_MS = 5000

interface UseFlipbookSubmittedRoundPollingParams {
  currentStep: FlipbookStep
  isRoundSubmitted: boolean
  realtimeConnectionStatus: FlipbookConnectionStatus
  roomCode: string | null
  syncActiveRoomProgress: (roomCode: string) => Promise<FlipbookRoomStateResponse | null>
}

export function useFlipbookSubmittedRoundPolling({
  currentStep,
  isRoundSubmitted,
  realtimeConnectionStatus,
  roomCode,
  syncActiveRoomProgress,
}: UseFlipbookSubmittedRoundPollingParams) {
  useEffect(() => {
    if (!roomCode || currentStep !== 'drawing' || !isRoundSubmitted) return
    if (realtimeConnectionStatus === 'connected') return

    let cancelled = false
    let pollingTimer: number | null = null

    const pollSubmittedRound = async () => {
      if (cancelled) return

      try {
        const nextRoomState = await syncActiveRoomProgress(roomCode)
        if (cancelled) return

        if (nextRoomState?.status === 'PLAYING') {
          pollingTimer = window.setTimeout(
            pollSubmittedRound,
            SUBMITTED_ROUND_POLLING_INTERVAL_MS,
          )
        }
      } catch {
        if (!cancelled) {
          pollingTimer = window.setTimeout(
            pollSubmittedRound,
            SUBMITTED_ROUND_POLLING_INTERVAL_MS,
          )
        }
      }
    }

    void pollSubmittedRound()

    return () => {
      cancelled = true
      if (pollingTimer !== null) {
        window.clearTimeout(pollingTimer)
      }
    }
  }, [
    currentStep,
    isRoundSubmitted,
    realtimeConnectionStatus,
    roomCode,
    syncActiveRoomProgress,
  ])
}
