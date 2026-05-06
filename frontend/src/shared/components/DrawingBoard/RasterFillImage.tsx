'use client'

import { useEffect, useState } from 'react'
import { Image as KonvaImage } from 'react-konva'

interface RasterFillImageProps {
  imageDataUrl: string
}

export default function RasterFillImage({ imageDataUrl }: RasterFillImageProps) {
  const [imageElement, setImageElement] = useState<HTMLImageElement | null>(null)

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      const nextImageElement = new window.Image()
      nextImageElement.onload = () => {
        if (!cancelled) {
          setImageElement(nextImageElement)
        }
      }
      nextImageElement.src = imageDataUrl
    })()

    return () => {
      cancelled = true
    }
  }, [imageDataUrl])

  if (!imageElement) return null

  return <KonvaImage image={imageElement} listening={false} />
}
