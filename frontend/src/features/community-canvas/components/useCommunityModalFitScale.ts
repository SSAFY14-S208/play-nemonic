'use client'

import { useEffect, useState } from 'react'

interface CommunityModalFitScaleOptions {
  designWidth: number
  designHeight: number
  maxWidth: number
  viewportPadding: number
}

function calculateFitScale({
  designWidth,
  designHeight,
  maxWidth,
  viewportPadding,
}: CommunityModalFitScaleOptions) {
  if (typeof window === 'undefined') {
    return maxWidth / designWidth
  }

  const visualViewport = window.visualViewport
  const viewportWidth = visualViewport?.width ?? window.innerWidth
  const viewportHeight = visualViewport?.height ?? window.innerHeight
  const availableWidth = Math.max(1, viewportWidth - viewportPadding)
  const availableHeight = Math.max(1, viewportHeight - viewportPadding)

  return Math.min(
    maxWidth / designWidth,
    availableWidth / designWidth,
    availableHeight / designHeight,
  )
}

export function useCommunityModalFitScale({
  designWidth,
  designHeight,
  maxWidth,
  viewportPadding,
}: CommunityModalFitScaleOptions) {
  const [scale, setScale] = useState(() =>
    calculateFitScale({ designWidth, designHeight, maxWidth, viewportPadding }),
  )

  useEffect(() => {
    const options = { designWidth, designHeight, maxWidth, viewportPadding }
    const updateScale = () => {
      setScale(calculateFitScale(options))
    }

    updateScale()
    window.addEventListener('resize', updateScale)
    window.visualViewport?.addEventListener('resize', updateScale)

    return () => {
      window.removeEventListener('resize', updateScale)
      window.visualViewport?.removeEventListener('resize', updateScale)
    }
  }, [designHeight, designWidth, maxWidth, viewportPadding])

  return scale
}

export function useCommunityCompactViewport() {
  const [isCompact, setCompact] = useState(false)

  useEffect(() => {
    const mediaQuery = window.matchMedia('(max-width: 767px), (max-height: 720px)')
    const updateCompact = () => setCompact(mediaQuery.matches)

    updateCompact()
    mediaQuery.addEventListener('change', updateCompact)

    return () => mediaQuery.removeEventListener('change', updateCompact)
  }, [])

  return isCompact
}
