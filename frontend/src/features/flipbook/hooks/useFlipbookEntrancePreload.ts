'use client'

import { useEffect, useRef } from 'react'

const DEFERRED_PRELOAD_TIMEOUT_MS = 1600
const DEFERRED_PRELOAD_FALLBACK_DELAY_MS = 700

type DeferredPreloadWindow = Window & {
  requestIdleCallback?: (callback: () => void, options?: { timeout?: number }) => number
  cancelIdleCallback?: (callbackId: number) => void
}

function scheduleDeferredPreload(onReady: () => void) {
  const browserWindow = window as DeferredPreloadWindow

  if (browserWindow.requestIdleCallback) {
    const idleCallbackId = browserWindow.requestIdleCallback(onReady, {
      timeout: DEFERRED_PRELOAD_TIMEOUT_MS,
    })

    return () => {
      browserWindow.cancelIdleCallback?.(idleCallbackId)
    }
  }

  const fallbackTimeoutId = window.setTimeout(onReady, DEFERRED_PRELOAD_FALLBACK_DELAY_MS)

  return () => {
    window.clearTimeout(fallbackTimeoutId)
  }
}

async function decodeImageElement(imageElement: HTMLImageElement) {
  if (imageElement.decode) {
    await imageElement.decode()
    return
  }

  await new Promise<void>((resolve, reject) => {
    imageElement.onload = () => resolve()
    imageElement.onerror = () => reject(new Error(`Failed to preload ${imageElement.src}`))
  })
}

export function useFlipbookEntrancePreload(imageSources: readonly string[]) {
  const preloadedImageElementsRef = useRef<HTMLImageElement[]>([])

  useEffect(() => {
    if (typeof window === 'undefined') return

    let cancelled = false
    const cancelDeferredPreload = scheduleDeferredPreload(() => {
      void (async () => {
        for (const imageSource of imageSources) {
          if (cancelled) return

          const imageElement = new window.Image()
          imageElement.decoding = 'async'
          imageElement.loading = 'lazy'
          imageElement.src = imageSource
          preloadedImageElementsRef.current.push(imageElement)

          try {
            await decodeImageElement(imageElement)
          } catch {
            // The visible frame will still request the image if deferred preload misses.
          }
        }
      })()
    })

    return () => {
      cancelled = true
      cancelDeferredPreload()
      preloadedImageElementsRef.current = []
    }
  }, [imageSources])
}
