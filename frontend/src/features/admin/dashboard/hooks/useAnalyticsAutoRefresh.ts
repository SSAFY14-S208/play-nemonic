'use client'

import { useEffect } from 'react'

import { AUTO_REFRESH_INTERVAL_MS } from '../constants'

// 30초 주기 자동 갱신 + 페이지 가시성 체크.
//
// document.visibilityState !== 'visible'인 동안 호출 스킵 → 백그라운드 탭에서
// 무의미한 호출 누적 방지. visible로 돌아오면 즉시 refresh 호출 → 데이터 신선도 보장.
//
// setInterval은 effect cleanup에서 clearInterval. visibilitychange listener도 동일.

export function useAnalyticsAutoRefresh(enabled: boolean, refresh: () => void) {
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

    const intervalId = window.setInterval(handleTick, AUTO_REFRESH_INTERVAL_MS)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      window.clearInterval(intervalId)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [enabled, refresh])
}
