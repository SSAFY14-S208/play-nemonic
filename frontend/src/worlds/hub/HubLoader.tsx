'use client'

import { useEffect, useState } from 'react'
import dynamic from 'next/dynamic'
import {
  DEFAULT_HUB_PERFORMANCE_MODE,
  getHubPerformanceModeFromSearch,
} from '@/shared/constants'
import type { HubPerformanceMode } from '@/shared/types'

const HubCanvas = dynamic(() => import('./HubCanvas'), { ssr: false })

function readHubPerformanceMode(): HubPerformanceMode {
  if (typeof window === 'undefined') {
    return DEFAULT_HUB_PERFORMANCE_MODE
  }

  return getHubPerformanceModeFromSearch(window.location.search)
}

export default function HubLoader() {
  const [performanceMode, setPerformanceMode] = useState<HubPerformanceMode>(
    readHubPerformanceMode,
  )

  useEffect(() => {
    const syncPerformanceMode = () => {
      setPerformanceMode(readHubPerformanceMode())
    }

    window.addEventListener('popstate', syncPerformanceMode)

    return () => {
      window.removeEventListener('popstate', syncPerformanceMode)
    }
  }, [])

  return <HubCanvas key={performanceMode} performanceMode={performanceMode} />
}
