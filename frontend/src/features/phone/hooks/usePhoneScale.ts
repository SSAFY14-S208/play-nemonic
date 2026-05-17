'use client'

import { useEffect, useState } from 'react'

const PHONE_DESIGN_WIDTH = 393
const PHONE_DESIGN_HEIGHT = 815
const VIEWPORT_PADDING_X = 32
const VIEWPORT_PADDING_Y = 48

function getVisibleViewportSize() {
  const visualViewport = window.visualViewport

  return {
    height: visualViewport?.height ?? window.innerHeight,
    width: visualViewport?.width ?? window.innerWidth,
  }
}

export function usePhoneScale() {
  const [scale, setScale] = useState(1)

  useEffect(() => {
    let animationFrameId = 0

    const recompute = () => {
      window.cancelAnimationFrame(animationFrameId)
      animationFrameId = window.requestAnimationFrame(() => {
        const { height, width } = getVisibleViewportSize()
        const availableWidth = width - VIEWPORT_PADDING_X
        const availableHeight = height - VIEWPORT_PADDING_Y

        setScale(
          Math.min(
            1,
            availableWidth / PHONE_DESIGN_WIDTH,
            availableHeight / PHONE_DESIGN_HEIGHT,
          ),
        )
      })
    }

    recompute()
    window.addEventListener('resize', recompute)
    window.visualViewport?.addEventListener('resize', recompute)
    window.visualViewport?.addEventListener('scroll', recompute)

    return () => {
      window.cancelAnimationFrame(animationFrameId)
      window.removeEventListener('resize', recompute)
      window.visualViewport?.removeEventListener('resize', recompute)
      window.visualViewport?.removeEventListener('scroll', recompute)
    }
  }, [])

  return scale
}
