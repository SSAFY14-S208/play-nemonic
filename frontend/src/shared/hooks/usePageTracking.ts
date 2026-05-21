'use client'

import { usePathname } from 'next/navigation'
import { useEffect, useRef } from 'react'

import { logEvent } from '@/shared/libs'

// route 변경 감지 → page_leave(이전 path + 체류 시간) → page_view(현재 path) 순서
export function usePageTracking() {
  const pathname = usePathname()
  const prevPathRef = useRef<string | null>(null)
  const enteredAtRef = useRef<number | null>(null)

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (cancelled) return

      const currentPath = pathname

      // 이전 경로가 있으면 page_leave 먼저
      if (prevPathRef.current !== null && prevPathRef.current !== currentPath) {
        const enteredAt = enteredAtRef.current
        const timeOnPage = enteredAt === null ? 0 : Date.now() - enteredAt
        logEvent('page_leave', {
          path: prevPathRef.current,
          metadata: {
            time_on_page_ms: timeOnPage,
            next_path: currentPath,
          },
        })
      }

      // page_view 기록
      logEvent('page_view', {
        path: currentPath,
        prevPath: prevPathRef.current ?? undefined,
      })

      prevPathRef.current = currentPath
      enteredAtRef.current = Date.now()
    })()

    return () => {
      cancelled = true
    }
  }, [pathname])
}
