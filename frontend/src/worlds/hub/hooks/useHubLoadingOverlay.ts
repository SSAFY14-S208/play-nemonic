import { useEffect, useRef, useState } from 'react'
import { useProgress } from '@react-three/drei'

const HUB_LOADING_MIN_VISIBLE_MS = 700
const HUB_LOADING_READY_HOLD_MS = 420
const HUB_LOADING_CACHE_FALLBACK_MS = 1500

function wait(durationMs: number) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, durationMs)
  })
}

function getRemainingMinimumVisibleMs(startedAt: number) {
  return Math.max(
    HUB_LOADING_MIN_VISIBLE_MS - (performance.now() - startedAt),
    0,
  )
}

function getHubLoadingStatusText({
  active,
  isCanvasReady,
  item,
}: {
  active: boolean
  isCanvasReady: boolean
  item: string
}) {
  if (!isCanvasReady) return '허브 입장 준비 중'
  if (!active) return '허브 정리 중'
  if (item.includes('/models/')) return '방 모델을 불러오는 중'
  if (item.includes('/textures/') || item.includes('/images/')) {
    return '공간의 색과 빛을 준비하는 중'
  }

  return '허브를 불러오는 중'
}

export function useHubLoadingOverlay(isCanvasReady: boolean) {
  const { active, item, progress } = useProgress()
  const [displayProgress, setDisplayProgress] = useState(0)
  const [isReady, setIsReady] = useState(false)
  const [isVisible, setIsVisible] = useState(true)
  const hasStartedLoadingRef = useRef(false)
  const visibleStartedAtRef = useRef(0)

  useEffect(() => {
    visibleStartedAtRef.current = performance.now()
  }, [])

  useEffect(() => {
    if (!active) return

    hasStartedLoadingRef.current = true
  }, [active])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      const nextProgress = isReady
        ? 100
        : isCanvasReady
          ? progress
          : Math.min(progress, 92)

      if (!cancelled) {
        setDisplayProgress((currentProgress) =>
          Math.max(currentProgress, nextProgress),
        )
      }
    })()

    return () => {
      cancelled = true
    }
  }, [isCanvasReady, isReady, progress])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (!isCanvasReady || progress < 100 || isReady) return

      await wait(
        getRemainingMinimumVisibleMs(
          visibleStartedAtRef.current || performance.now(),
        ),
      )
      if (cancelled) return

      setDisplayProgress(100)
      setIsReady(true)
    })()

    return () => {
      cancelled = true
    }
  }, [isCanvasReady, isReady, progress])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (!isCanvasReady) return

      await wait(HUB_LOADING_CACHE_FALLBACK_MS)
      if (
        cancelled ||
        isReady ||
        hasStartedLoadingRef.current
      ) {
        return
      }

      setDisplayProgress(100)
      setIsReady(true)
    })()

    return () => {
      cancelled = true
    }
  }, [isCanvasReady, isReady])

  useEffect(() => {
    if (!isReady) return

    const hideTimerId = window.setTimeout(() => {
      setIsVisible(false)
    }, HUB_LOADING_READY_HOLD_MS)

    return () => {
      window.clearTimeout(hideTimerId)
    }
  }, [isReady])

  return {
    displayProgress: Math.round(displayProgress),
    isReady,
    isVisible,
    statusText: getHubLoadingStatusText({
      active,
      isCanvasReady,
      item,
    }),
  }
}
