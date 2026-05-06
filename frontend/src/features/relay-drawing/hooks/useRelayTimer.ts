'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useRelayDrawingStore } from '../relayDrawingStore'

const EXPIRING_THRESHOLD_SECONDS = 10

interface UseRelayTimerReturn {
  remainingSeconds: number
  isExpiring: boolean
  formattedTime: string
  syncRemainingTime: (seconds: number) => void
}

export function useRelayTimer(): UseRelayTimerReturn {
  const timeLimitSeconds = useRelayDrawingStore((state) => state.timeLimitSeconds)
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey)
  const currentStep = useRelayDrawingStore((state) => state.currentStep)
  const completeRound = useRelayDrawingStore((state) => state.completeRound)

  const [remainingSeconds, setRemainingSeconds] = useState(timeLimitSeconds)
  const hasExpiredRef = useRef(false)

  useEffect(() => {
    setRemainingSeconds(timeLimitSeconds)
    hasExpiredRef.current = false
  }, [activeRoundKey, timeLimitSeconds])

  useEffect(() => {
    if (currentStep !== 'drawing') return

    const intervalId = setInterval(() => {
      setRemainingSeconds((previous) => {
        if (previous <= 1 && !hasExpiredRef.current) {
          hasExpiredRef.current = true
          queueMicrotask(() => completeRound())
          return 0
        }
        return Math.max(previous - 1, 0)
      })
    }, 1000)

    return () => clearInterval(intervalId)
  }, [currentStep, activeRoundKey, completeRound])

  const syncRemainingTime = useCallback((seconds: number) => {
    setRemainingSeconds(seconds)
    hasExpiredRef.current = false
  }, [])

  const isExpiring = remainingSeconds <= EXPIRING_THRESHOLD_SECONDS
  const minutes = Math.floor(remainingSeconds / 60)
  const seconds = remainingSeconds % 60
  const formattedTime = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`

  return { remainingSeconds, isExpiring, formattedTime, syncRemainingTime }
}
