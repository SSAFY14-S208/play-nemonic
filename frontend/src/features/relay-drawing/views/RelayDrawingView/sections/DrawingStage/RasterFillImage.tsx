'use client'

import { useEffect, useState } from 'react'
import { Image as KonvaImage } from 'react-konva'

interface RasterFillImageProps {
  imageDataUrl: string
  compositeOperation?: GlobalCompositeOperation
  opacity?: number
  yOffset?: number
}

export default function RasterFillImage({
  imageDataUrl,
  compositeOperation = 'source-over',
  opacity = 1,
  yOffset = 0,
}: RasterFillImageProps) {
  const [imageElement, setImageElement] = useState<HTMLImageElement | null>(null)

  useEffect(() => {
    let isCancelled = false
    const nextImageElement = new window.Image()

    nextImageElement.onload = () => {
      if (!isCancelled) {
        setImageElement(nextImageElement)
      }
    }
    nextImageElement.src = imageDataUrl

    return () => {
      isCancelled = true
    }
  }, [imageDataUrl])

  if (!imageElement) return null

  return (
    <KonvaImage
      image={imageElement}
      x={0}
      y={yOffset}
      opacity={opacity}
      listening={false}
      globalCompositeOperation={compositeOperation}
    />
  )
}
