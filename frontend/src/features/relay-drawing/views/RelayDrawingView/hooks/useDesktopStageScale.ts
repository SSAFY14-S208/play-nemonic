'use client'

import { useEffect, useRef, useState } from 'react'

const DESKTOP_DESIGN_WIDTH = 1536
const DESKTOP_DESIGN_HEIGHT = 1024

export function useDesktopStageScale() {
  const desktopWrapperRef = useRef<HTMLDivElement>(null)
  const [desktopScale, setDesktopScale] = useState(0)

  useEffect(() => {
    const wrapper = desktopWrapperRef.current
    if (!wrapper) return

    const updateScale = () => {
      const rect = wrapper.getBoundingClientRect()
      if (rect.width === 0 || rect.height === 0) return

      const widthRatio = rect.width / DESKTOP_DESIGN_WIDTH
      const heightRatio = rect.height / DESKTOP_DESIGN_HEIGHT
      setDesktopScale(Math.min(widthRatio, heightRatio, 1))
    }

    const animationFrameId = requestAnimationFrame(updateScale)
    const observer = new ResizeObserver(updateScale)
    observer.observe(wrapper)

    return () => {
      cancelAnimationFrame(animationFrameId)
      observer.disconnect()
    }
  }, [])

  return {
    desktopWrapperRef,
    desktopScale,
    desktopDesignWidth: DESKTOP_DESIGN_WIDTH,
    desktopDesignHeight: DESKTOP_DESIGN_HEIGHT,
  }
}
