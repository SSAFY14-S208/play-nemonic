'use client'

import { useEffect, useRef } from 'react'

import { logEvent, touchSession } from '@/shared/libs'

const ACTIVE_INTERVAL_MS = 30_000 // 활성 탭 30초
const INACTIVE_INTERVAL_MS = 5 * 60_000 // 비활성 탭 5분

// visibility 기반 interval 전환 heartbeat
export function useClientAlive() {
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const sessionStartRef = useRef<number | null>(null)

  useEffect(() => {
    let cancelled = false
    sessionStartRef.current = Date.now()

    function sendHeartbeat() {
      if (cancelled) return
      touchSession()
      logEvent('client_alive', {
        path: window.location.pathname,
        metadata: {
          time_in_session_ms: Date.now() - (sessionStartRef.current ?? Date.now()),
        },
      })
    }

    function startInterval(intervalMs: number) {
      if (timerRef.current !== null) {
        clearInterval(timerRef.current)
      }
      timerRef.current = setInterval(sendHeartbeat, intervalMs)
    }

    function handleVisibilityChange() {
      if (cancelled) return
      const isVisible = document.visibilityState === 'visible'
      startInterval(isVisible ? ACTIVE_INTERVAL_MS : INACTIVE_INTERVAL_MS)
    }

    // 초기 시작
    startInterval(ACTIVE_INTERVAL_MS)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      cancelled = true
      if (timerRef.current !== null) {
        clearInterval(timerRef.current)
        timerRef.current = null
      }
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [])
}
