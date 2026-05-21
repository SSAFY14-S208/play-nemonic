'use client'

import { useEffect } from 'react'

import { logEvent } from '@/shared/libs'

// web-vitals dynamic import → LCP/INP/CLS/TTFB 수집 (10% sampling은 logger에서 처리)
export function useWebVitals() {
  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (cancelled) return

      const { onLCP, onINP, onCLS, onTTFB } = await import('web-vitals')

      const reportMetric = (metric: { name: string; value: number; rating: string }) => {
        if (cancelled) return
        logEvent('web_vitals', {
          path: window.location.pathname,
          metadata: {
            metric_name: metric.name,
            value: metric.value,
            rating: metric.rating,
          },
        })
      }

      onLCP(reportMetric)
      onINP(reportMetric)
      onCLS(reportMetric)
      onTTFB(reportMetric)
    })()

    return () => {
      cancelled = true
    }
  }, [])
}
