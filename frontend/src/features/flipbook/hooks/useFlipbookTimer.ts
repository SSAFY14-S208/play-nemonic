'use client'

import { useCallback, useEffect, useState } from 'react'
import type { FlipbookStep, FlipbookTimeLimitSeconds } from '../constants'

export function useFlipbookTimer({
  activeRoundIndex,
  currentStep,
  deadlineAt,
  initialRemainingSeconds,
  selectedTimeLimitSeconds,
  onTimeExpired,
}: {
  activeRoundIndex: number
  currentStep: FlipbookStep
  deadlineAt?: string | null
  initialRemainingSeconds?: number | null
  selectedTimeLimitSeconds: FlipbookTimeLimitSeconds
  onTimeExpired: () => void
}) {
  const [remainingSeconds, setRemainingSeconds] = useState<number>(selectedTimeLimitSeconds)

  const getServerRemainingSeconds = useCallback(() => {
    if (deadlineAt) {
      return Math.max(0, Math.ceil((new Date(deadlineAt).getTime() - Date.now()) / 1000))
    }

    return initialRemainingSeconds ?? selectedTimeLimitSeconds
  }, [deadlineAt, initialRemainingSeconds, selectedTimeLimitSeconds])

  const resetRemainingSeconds = useCallback(() => {
    setRemainingSeconds(getServerRemainingSeconds())
  }, [getServerRemainingSeconds])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (currentStep !== 'drawing') return
      if (!cancelled) {
        setRemainingSeconds(getServerRemainingSeconds())
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeRoundIndex, currentStep, getServerRemainingSeconds])

  useEffect(() => {
    if (currentStep !== 'drawing') return

    const timerId = window.setInterval(() => {
      if (deadlineAt) {
        setRemainingSeconds(
          Math.max(0, Math.ceil((new Date(deadlineAt).getTime() - Date.now()) / 1000)),
        )
        return
      }

      setRemainingSeconds((currentSeconds) => Math.max(0, currentSeconds - 1))
    }, 1000)

    return () => window.clearInterval(timerId)
  }, [activeRoundIndex, currentStep, deadlineAt])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (currentStep === 'drawing' && remainingSeconds === 0 && !cancelled) {
        onTimeExpired()
      }
    })()

    return () => {
      cancelled = true
    }
  }, [currentStep, onTimeExpired, remainingSeconds])

  return {
    remainingSeconds,
    resetRemainingSeconds,
  }
}
