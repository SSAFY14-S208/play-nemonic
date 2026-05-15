'use client'

import { useCallback, useRef, useState, type RefObject, type UIEvent, type WheelEvent } from 'react'

const WHEEL_FRAME_THRESHOLD = 72

export function useFlipbookEntranceWheelFrames(
  scrollZoneRef: RefObject<HTMLElement | null>,
  frameCount: number,
  enabled: boolean,
) {
  const [activeFrameIndex, setActiveFrameIndex] = useState(0)
  const accumulatedWheelDeltaRef = useRef(0)

  const handleWheel = useCallback(
    (event: WheelEvent<HTMLElement>) => {
      if (!enabled || frameCount <= 0) return
      const scrollZoneElement = scrollZoneRef.current
      if (!scrollZoneElement) return

      const scrollZoneRect = scrollZoneElement.getBoundingClientRect()
      const isWheelInsideScrollZone =
        event.clientX >= scrollZoneRect.left &&
        event.clientX <= scrollZoneRect.right &&
        event.clientY >= scrollZoneRect.top &&
        event.clientY <= scrollZoneRect.bottom
      if (!isWheelInsideScrollZone) return

      event.preventDefault()
      accumulatedWheelDeltaRef.current += event.deltaY

      if (Math.abs(accumulatedWheelDeltaRef.current) < WHEEL_FRAME_THRESHOLD) return

      const frameDirection = accumulatedWheelDeltaRef.current > 0 ? 1 : -1
      accumulatedWheelDeltaRef.current = 0
      setActiveFrameIndex((currentFrameIndex) => {
        const nextFrameIndex = currentFrameIndex + frameDirection

        return Math.min(frameCount - 1, Math.max(0, nextFrameIndex))
      })
    },
    [enabled, frameCount, scrollZoneRef],
  )

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
    handleWheel,
  }
}
