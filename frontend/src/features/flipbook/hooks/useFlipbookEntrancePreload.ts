'use client'

import { useEffect, useRef } from 'react'

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
    const imageElements = imageSources.map((imageSource) => {
      const imageElement = new window.Image()
      imageElement.decoding = 'async'
      imageElement.loading = 'eager'
      imageElement.src = imageSource

      return imageElement
    })

    preloadedImageElementsRef.current = imageElements

    void Promise.allSettled(
      imageElements.map(async (imageElement) => {
        if (cancelled) return

        await decodeImageElement(imageElement)
      }),
    ).catch(() => {
      // The visible frame will still request any image if preload misses.
    })

    return () => {
      cancelled = true
      preloadedImageElementsRef.current = []
    }
  }, [imageSources])
}
