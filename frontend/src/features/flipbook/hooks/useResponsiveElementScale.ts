'use client'

import { useEffect, useRef, useState } from 'react'

interface UseResponsiveElementScaleOptions {
  sourceWidth: number
  sourceHeight: number
  maxScale?: number
}

export function useResponsiveElementScale({
  sourceWidth,
  sourceHeight,
  maxScale = 1,
}: UseResponsiveElementScaleOptions) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [elementScale, setElementScale] = useState(0)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const updateElementScale = () => {
      const containerRect = container.getBoundingClientRect()
      if (containerRect.width === 0 || containerRect.height === 0) return

      const widthRatio = containerRect.width / sourceWidth
      const heightRatio = containerRect.height / sourceHeight
      setElementScale(Math.min(widthRatio, heightRatio, maxScale))
    }

    const animationFrame = window.requestAnimationFrame(updateElementScale)
    const resizeObserver = new ResizeObserver(updateElementScale)
    resizeObserver.observe(container)

    return () => {
      window.cancelAnimationFrame(animationFrame)
      resizeObserver.disconnect()
    }
  }, [maxScale, sourceHeight, sourceWidth])

  return {
    containerRef,
    elementScale,
  }
}
