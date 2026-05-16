'use client'

import { useEffect, useState } from 'react'
import dynamic from 'next/dynamic'
import {
  DEFAULT_HUB_PERFORMANCE_MODE,
  getHubFocusKeyFromSearch,
  getHubPerformanceModeFromSearch,
} from '@/shared/constants'
import { useHubRoomStore } from '@/shared/stores'
import type { HubPerformanceMode } from '@/shared/types'

const HubCanvas = dynamic(() => import('./HubCanvas'), { ssr: false })

function readHubPerformanceMode(): HubPerformanceMode {
  if (typeof window === 'undefined') {
    return DEFAULT_HUB_PERFORMANCE_MODE
  }

  return getHubPerformanceModeFromSearch(window.location.search)
}

export default function HubLoader() {
  const setFocus = useHubRoomStore((state) => state.setFocus)
  const [performanceMode, setPerformanceMode] = useState<HubPerformanceMode>(
    readHubPerformanceMode,
  )

  useEffect(() => {
    const syncHubRuntimeSearch = () => {
      setPerformanceMode(readHubPerformanceMode())

      const nextFocusKey = getHubFocusKeyFromSearch(window.location.search)

      if (nextFocusKey) {
        setFocus(nextFocusKey)
      }
    }

    let cancelled = false

    ;(async () => {
      await Promise.resolve()

      if (!cancelled) {
        syncHubRuntimeSearch()
      }
    })()

    window.addEventListener('popstate', syncHubRuntimeSearch)

    return () => {
      cancelled = true
      window.removeEventListener('popstate', syncHubRuntimeSearch)
    }
  }, [setFocus])

  return <HubCanvas key={performanceMode} performanceMode={performanceMode} />
}
