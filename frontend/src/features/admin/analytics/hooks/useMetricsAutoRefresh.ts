'use client'

import { useEffect } from 'react'

import { METRICS_AUTO_REFRESH_INTERVAL_MS } from '..'

// 15초 주기 자동 갱신 + 페이지 가시성 체크. dashboard의 useAnalyticsAutoRefresh와
// 동일 패턴이지만 주기 상수가 다름.

export function useMetricsAutoRefresh(enabled: boolean, refresh: () => void) {
  useEffect(() => {
    if (!enabled) return

    const handleTick = () => {
      if (
        typeof document !== 'undefined' &&
        document.visibilityState !== 'visible'
      ) {
        return
      }
      refresh()
    }

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        refresh()
      }
    }

    const intervalId = window.setInterval(handleTick, METRICS_AUTO_REFRESH_INTERVAL_MS)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      window.clearInterval(intervalId)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [enabled, refresh])
}
