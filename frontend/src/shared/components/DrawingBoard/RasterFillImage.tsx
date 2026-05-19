'use client'

import { useEffect, useState } from 'react'
import { Image as KonvaImage, Text } from 'react-konva'

interface RasterFillImageProps {
  imageDataUrl: string
  width: number
  height: number
  compositeOperation?: GlobalCompositeOperation
}

export default function RasterFillImage({
  imageDataUrl,
  width,
  height,
  compositeOperation = 'source-over',
}: RasterFillImageProps) {
  const [imageElement, setImageElement] = useState<HTMLImageElement | null>(null)
  const [loadFailed, setLoadFailed] = useState(false)

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      setLoadFailed(false)
      const nextImageElement = new window.Image()
      if (imageDataUrl.startsWith('http://') || imageDataUrl.startsWith('https://')) {
        nextImageElement.crossOrigin = 'anonymous'
      }
      nextImageElement.onload = () => {
        if (!cancelled) {
          setImageElement(nextImageElement)
        }
      }
      nextImageElement.onerror = () => {
        if (!cancelled) {
          setLoadFailed(true)
          console.warn('드로잉 이미지 로딩에 실패했습니다.', imageDataUrl)
        }
      }
      nextImageElement.src = imageDataUrl
    })()

    return () => {
      cancelled = true
    }
  }, [imageDataUrl])

  if (loadFailed) {
    return (
      <Text
        x={0}
        y={height / 2 - 12}
        width={width}
        align="center"
        text="이미지 로딩 실패"
        fontSize={18}
        fontStyle="bold"
        fill="#ac626a"
        listening={false}
      />
    )
  }

  if (!imageElement) return null

  return (
    <KonvaImage
      image={imageElement}
      width={width}
      height={height}
      listening={false}
      globalCompositeOperation={compositeOperation}
    />
  )
}
