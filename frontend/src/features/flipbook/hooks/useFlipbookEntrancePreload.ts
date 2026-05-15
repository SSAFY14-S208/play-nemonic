'use client'

import { useEffect, useRef } from 'react'

const IMAGE_PRELOAD_REL = 'preload'
const IMAGE_PRELOAD_AS = 'image'

function getImageMimeType(imageSource: string) {
  if (imageSource.endsWith('.webp')) return 'image/webp'
  if (imageSource.endsWith('.png')) return 'image/png'

  return undefined
}

export function useFlipbookEntrancePreload(imageSources: readonly string[]) {
  const preloadedImageElementsRef = useRef<HTMLImageElement[]>([])

  useEffect(() => {
    if (typeof window === 'undefined') return

    let cancelled = false
    const preloadLinks = imageSources.map((imageSource) => {
      const preloadLink = document.createElement('link')
      preloadLink.rel = IMAGE_PRELOAD_REL
      preloadLink.as = IMAGE_PRELOAD_AS
      const mimeType = getImageMimeType(imageSource)
      if (mimeType) {
        preloadLink.type = mimeType
      }
      preloadLink.href = imageSource
      preloadLink.setAttribute('fetchpriority', 'high')
      document.head.appendChild(preloadLink)

      return preloadLink
    })

    ;(async () => {
      const imageElements = imageSources.map((imageSource) => {
        const imageElement = new window.Image()
        imageElement.decoding = 'async'
        imageElement.loading = 'eager'
        imageElement.src = imageSource

        return imageElement
      })

      preloadedImageElementsRef.current = imageElements

      await Promise.allSettled(
        imageElements.map(async (imageElement) => {
          if (imageElement.decode) {
            await imageElement.decode()
            return
          }

          await new Promise<void>((resolve, reject) => {
            imageElement.onload = () => resolve()
            imageElement.onerror = () => reject(new Error(`Failed to preload ${imageElement.src}`))
          })
        }),
      )

      if (cancelled) {
        preloadedImageElementsRef.current = []
      }
    })()

    return () => {
      cancelled = true
      preloadedImageElementsRef.current = []
      preloadLinks.forEach((preloadLink) => {
        preloadLink.remove()
      })
    }
  }, [imageSources])
}
