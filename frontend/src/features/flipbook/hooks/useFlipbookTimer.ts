'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { parseServerInstant } from '@/shared/utils'
import type { FlipbookStep, FlipbookTimeLimitSeconds } from '../types'

const TIMER_DRIFT_GRACE_SECONDS = 10
const MILLISECOND_LIKE_REMAINING_THRESHOLD = 1000
const HAS_TIMEZONE_SUFFIX = /Z$|[+-]\d{2}:?\d{2}$/

function normalizeRemainingSeconds(
  remainingSeconds: number | null | undefined,
  selectedTimeLimitSeconds: FlipbookTimeLimitSeconds,
) {
  if (remainingSeconds === null || remainingSeconds === undefined) {
    return selectedTimeLimitSeconds
  }

  const seconds =
    remainingSeconds > MILLISECOND_LIKE_REMAINING_THRESHOLD
      ? Math.ceil(remainingSeconds / 1000)
      : Math.ceil(remainingSeconds)

  if (seconds < 0) return 0

  const maximumExpectedSeconds = selectedTimeLimitSeconds + TIMER_DRIFT_GRACE_SECONDS
  if (seconds > maximumExpectedSeconds) {
    return selectedTimeLimitSeconds
  }

  return seconds
}

function getDeadlineRemainingSeconds({
  deadlineAt,
  initialRemainingSeconds,
  selectedTimeLimitSeconds,
}: {
  deadlineAt: string
  initialRemainingSeconds?: number | null
  selectedTimeLimitSeconds: FlipbookTimeLimitSeconds
}) {
  const maximumExpectedSeconds = selectedTimeLimitSeconds + TIMER_DRIFT_GRACE_SECONDS
  const serverRemainingSeconds = Math.max(
    0,
    Math.ceil((parseServerInstant(deadlineAt).getTime() - Date.now()) / 1000),
  )

  if (serverRemainingSeconds <= maximumExpectedSeconds) {
    return serverRemainingSeconds
  }

  if (!HAS_TIMEZONE_SUFFIX.test(deadlineAt)) {
    const localRemainingSeconds = Math.max(
      0,
      Math.ceil((new Date(deadlineAt).getTime() - Date.now()) / 1000),
    )

    if (localRemainingSeconds <= maximumExpectedSeconds) {
      return localRemainingSeconds
    }
  }

  return normalizeRemainingSeconds(initialRemainingSeconds, selectedTimeLimitSeconds)
}

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
      return getDeadlineRemainingSeconds({
        deadlineAt,
        initialRemainingSeconds,
        selectedTimeLimitSeconds,
      })
    }

    return normalizeRemainingSeconds(initialRemainingSeconds, selectedTimeLimitSeconds)
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
        const nextRemainingSeconds = getDeadlineRemainingSeconds({
          deadlineAt,
          initialRemainingSeconds,
          selectedTimeLimitSeconds,
        })
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
  }, [activeRoundIndex, currentStep, deadlineAt, initialRemainingSeconds, selectedTimeLimitSeconds])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (currentStep !== 'drawing') {
        expiredRoundKeyRef.current = null
        positiveCountdownRoundKeyRef.current = null
        return
      }

      const roundExpirationKey = `${activeRoundIndex}:${deadlineAt ?? 'local'}`
      const hasServerDeadline = Boolean(deadlineAt)
      if (
        remainingSeconds === 0 &&
        (hasServerDeadline || positiveCountdownRoundKeyRef.current === roundExpirationKey) &&
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
