'use client'

import { useCallback, useEffect, useState } from 'react'
import dynamic from 'next/dynamic'
import {
  DEFAULT_HUB_PERFORMANCE_MODE,
  getHubFocusKeyFromSearch,
  getHubPerformanceModeFromSearch,
} from '@/shared/constants'
import { useHubRoomStore } from '@/shared/stores'
import type { HubPerformanceMode } from '@/shared/types'
import HubLoadingOverlay from './HubLoadingOverlay'

const HubCanvas = dynamic(() => import('./HubCanvas'), { ssr: false })

function readHubPerformanceMode(): HubPerformanceMode {
  if (typeof window === 'undefined') {
    return DEFAULT_HUB_PERFORMANCE_MODE
  }

  return getHubPerformanceModeFromSearch(window.location.search)
}

export default function HubLoader() {
  const setFocus = useHubRoomStore((state) => state.setFocus)
  const [isCanvasReady, setIsCanvasReady] = useState(false)
  const [shouldMountCanvas, setShouldMountCanvas] = useState(false)
  const [performanceMode, setPerformanceMode] = useState<HubPerformanceMode>(
    readHubPerformanceMode,
  )
  const handleCanvasReady = useCallback(() => {
    setIsCanvasReady(true)
  }, [])

  useEffect(() => {
    let firstPaintFrameId = 0
    let secondPaintFrameId = 0
    let cancelled = false

    firstPaintFrameId = window.requestAnimationFrame(() => {
      secondPaintFrameId = window.requestAnimationFrame(() => {
        if (!cancelled) {
          setShouldMountCanvas(true)
        }
      })
    })

    return () => {
      cancelled = true
      window.cancelAnimationFrame(firstPaintFrameId)
      window.cancelAnimationFrame(secondPaintFrameId)
    }
  }, [])

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

  return (
    <>
      {shouldMountCanvas && (
        <HubCanvas
          key={performanceMode}
          onCanvasReady={handleCanvasReady}
          performanceMode={performanceMode}
        />
      )}
      <HubLoadingOverlay isCanvasReady={isCanvasReady} />
    </>
  )
}
