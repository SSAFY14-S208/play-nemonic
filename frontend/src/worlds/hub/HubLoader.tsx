'use client'

import { useCallback, useEffect, useState } from 'react'
import dynamic from 'next/dynamic'
import { getHubFocusKeyFromSearch } from '@/shared/constants'
import { useHubRoomStore } from '@/shared/stores'
import HubLoadingOverlay from './HubLoadingOverlay'

const RoomPreviewCanvas = dynamic(
  () => import('../room-preview/RoomPreviewCanvas'),
  { ssr: false },
)

export default function HubLoader() {
  const setFocus = useHubRoomStore((state) => state.setFocus)
  const [isCanvasReady, setIsCanvasReady] = useState(false)
  const [shouldMountCanvas, setShouldMountCanvas] = useState(false)
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
      const nextFocusKey = getHubFocusKeyFromSearch(window.location.search)
      setFocus(nextFocusKey ?? 'overview')
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
        <RoomPreviewCanvas
          className="z-[1]"
          onCanvasReady={handleCanvasReady}
          variant="hub"
        />
      )}
      <HubLoadingOverlay isCanvasReady={isCanvasReady} />
    </>
  )
}
