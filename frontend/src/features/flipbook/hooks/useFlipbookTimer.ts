'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { parseServerInstant } from '@/shared/utils'
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
  const expiredRoundKeyRef = useRef<string | null>(null)
  const positiveCountdownRoundKeyRef = useRef<string | null>(null)

  const getServerRemainingSeconds = useCallback(() => {
    if (deadlineAt) {
      return Math.max(0, Math.ceil((parseServerInstant(deadlineAt).getTime() - Date.now()) / 1000))
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
      const roundExpirationKey = `${activeRoundIndex}:${deadlineAt ?? 'local'}`
      const nextRemainingSeconds = getServerRemainingSeconds()
      if (!cancelled) {
        setRemainingSeconds(nextRemainingSeconds)
        if (nextRemainingSeconds > 0) {
          positiveCountdownRoundKeyRef.current = roundExpirationKey
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeRoundIndex, currentStep, deadlineAt, getServerRemainingSeconds])

  useEffect(() => {
    if (currentStep !== 'drawing') return

    const timerId = window.setInterval(() => {
      if (deadlineAt) {
        const roundExpirationKey = `${activeRoundIndex}:${deadlineAt}`
        const nextRemainingSeconds = Math.max(
          0,
          Math.ceil((parseServerInstant(deadlineAt).getTime() - Date.now()) / 1000),
        )
        if (nextRemainingSeconds > 0) {
          positiveCountdownRoundKeyRef.current = roundExpirationKey
        }
        setRemainingSeconds(nextRemainingSeconds)
        return
      }

      setRemainingSeconds((currentSeconds) => {
        const nextRemainingSeconds = Math.max(0, currentSeconds - 1)
        if (nextRemainingSeconds > 0) {
          positiveCountdownRoundKeyRef.current = `${activeRoundIndex}:local`
        }
        return nextRemainingSeconds
      })
    }, 1000)

    return () => window.clearInterval(timerId)
  }, [activeRoundIndex, currentStep, deadlineAt])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (currentStep !== 'drawing') {
        expiredRoundKeyRef.current = null
        positiveCountdownRoundKeyRef.current = null
        return
      }

      const roundExpirationKey = `${activeRoundIndex}:${deadlineAt ?? 'local'}`
      if (
        remainingSeconds === 0 &&
        positiveCountdownRoundKeyRef.current === roundExpirationKey &&
        expiredRoundKeyRef.current !== roundExpirationKey &&
        !cancelled
      ) {
        expiredRoundKeyRef.current = roundExpirationKey
        onTimeExpired()
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeRoundIndex, currentStep, deadlineAt, onTimeExpired, remainingSeconds])

  return {
    remainingSeconds,
    resetRemainingSeconds,
  }
}
