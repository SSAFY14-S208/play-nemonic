'use client'

import { useCallback, useEffect } from 'react'

import { getFlipbookRoomResult } from '@/shared/apis'
import type { FlipbookStep } from '../types'
import { shouldIgnoreInactiveFlipbookRoom } from '../utils'

const RESULT_POLLING_INTERVAL_MS = 1500

interface UseFlipbookResultPollingParams {
  currentStep: FlipbookStep
  isDummyResultPreview: boolean
  isResultReady: boolean
  participantCount: number
  roomCode: string | null
  setIsResultReady: (isResultReady: boolean) => void
  setResultCount: (resultCount: number) => void
  showReadyResult: (params: {
    resultItems: Awaited<ReturnType<typeof getFlipbookRoomResult>>['results']
    resultParticipantCount: number
    targetRoomCode: string | null
  }) => void
}

export function useFlipbookResultPolling({
  currentStep,
  isDummyResultPreview,
  isResultReady,
  participantCount,
  roomCode,
  setIsResultReady,
  setResultCount,
  showReadyResult,
}: UseFlipbookResultPollingParams) {
  const fetchResult = useCallback(
    async (targetRoomCode = roomCode, resultParticipantCount = participantCount) => {
      if (!targetRoomCode) return null
      if (shouldIgnoreInactiveFlipbookRoom(targetRoomCode)) return null

      const nextResult = await getFlipbookRoomResult(targetRoomCode)
      if (shouldIgnoreInactiveFlipbookRoom(targetRoomCode)) return null

      if (nextResult.ready) {
        showReadyResult({
          resultItems: nextResult.results,
          resultParticipantCount,
          targetRoomCode,
        })
      } else {
        setResultCount(nextResult.resultCount)
        setIsResultReady(false)
      }

      return nextResult
    },
    [participantCount, roomCode, setIsResultReady, setResultCount, showReadyResult],
  )

  useEffect(() => {
    if (isDummyResultPreview || currentStep !== 'result' || !roomCode || isResultReady) return

    let cancelled = false
    let pollingTimer: number | null = null
    const pollResult = async () => {
      if (cancelled) return

      try {
        const nextResult = await fetchResult(roomCode)
        if (!nextResult?.ready && !cancelled) {
          pollingTimer = window.setTimeout(pollResult, RESULT_POLLING_INTERVAL_MS)
        }
      } catch {
        if (!cancelled) {
          pollingTimer = window.setTimeout(pollResult, RESULT_POLLING_INTERVAL_MS)
        }
      }
    }

    void pollResult()

    return () => {
      cancelled = true
      if (pollingTimer !== null) {
        window.clearTimeout(pollingTimer)
      }
    }
  }, [currentStep, fetchResult, isDummyResultPreview, isResultReady, roomCode])

  return {
    fetchResult,
  }
}

export type FlipbookResultPolling = ReturnType<typeof useFlipbookResultPolling>
