'use client'

import { useCallback, useState, type UIEvent } from 'react'

const FRAME_SCROLL_STEP_PIXELS = 72

export function useFlipbookEntranceWheelFrames(
  frameCount: number,
  enabled: boolean,
) {
  const [activeFrameIndex, setActiveFrameIndex] = useState(0)
  const scrollSpacerHeight = `calc(100% + ${Math.max(0, frameCount - 1) * FRAME_SCROLL_STEP_PIXELS}px)`

  const handleScroll = useCallback(
    (event: UIEvent<HTMLElement>) => {
      if (!enabled || frameCount <= 0) return

      const scrollableElement = event.currentTarget
      const maxScrollTop = scrollableElement.scrollHeight - scrollableElement.clientHeight
      if (maxScrollTop <= 0) return

      const scrollProgress = scrollableElement.scrollTop / maxScrollTop
      const nextFrameIndex = Math.min(
        frameCount - 1,
        Math.max(0, Math.round(scrollProgress * (frameCount - 1))),
      )
      setActiveFrameIndex(nextFrameIndex)
    },
    [enabled, frameCount],
  )

  return {
    activeFrameIndex,
    handleScroll,
    scrollSpacerHeight,
  }
}
