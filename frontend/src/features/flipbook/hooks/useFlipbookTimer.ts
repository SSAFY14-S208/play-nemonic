'use client'

import { useCallback, useEffect, useState } from 'react'
import type { FlipbookStep, FlipbookTimeLimitSeconds } from '../constants'

export function useFlipbookTimer({
  activeRoundIndex,
  currentStep,
  selectedTimeLimitSeconds,
  onTimeExpired,
}: {
  activeRoundIndex: number
  currentStep: FlipbookStep
  selectedTimeLimitSeconds: FlipbookTimeLimitSeconds
  onTimeExpired: () => void
}) {
  const [remainingSeconds, setRemainingSeconds] = useState<number>(selectedTimeLimitSeconds)

  const resetRemainingSeconds = useCallback(() => {
    setRemainingSeconds(selectedTimeLimitSeconds)
  }, [selectedTimeLimitSeconds])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (currentStep !== 'drawing') return
      if (!cancelled) {
        setRemainingSeconds(selectedTimeLimitSeconds)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeRoundIndex, currentStep, selectedTimeLimitSeconds])

  useEffect(() => {
    if (currentStep !== 'drawing') return

    const timerId = window.setInterval(() => {
      setRemainingSeconds((currentSeconds) => Math.max(0, currentSeconds - 1))
    }, 1000)

    return () => window.clearInterval(timerId)
  }, [currentStep, activeRoundIndex])

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
