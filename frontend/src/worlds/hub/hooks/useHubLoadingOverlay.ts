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
  const targetProgressRef = useRef(0)

  const targetProgress = isReady
    ? 100
    : isCanvasReady
      ? progress
      : Math.min(progress, 92)
  targetProgressRef.current = Math.max(targetProgressRef.current, targetProgress)

  useEffect(() => {
    visibleStartedAtRef.current = performance.now()
  }, [])

  useEffect(() => {
    if (!active) return

    hasStartedLoadingRef.current = true
  }, [active])

  useEffect(() => {
    let cancelled = false
    let animationFrameHandle = 0
    let lastTimestamp: number | null = null

    const animate = (timestamp: number) => {
      if (cancelled) return

      if (lastTimestamp !== null) {
        const deltaSeconds = (timestamp - lastTimestamp) / 1000

        setDisplayProgress((current) => {
          const target = targetProgressRef.current
          if (current >= target) return current

          const remaining = target - current
          const speed = Math.max(remaining * 2, 12)
          const step = Math.min(speed * deltaSeconds, remaining)
          return current + step
        })
      }

      lastTimestamp = timestamp
      animationFrameHandle = requestAnimationFrame(animate)
    }

    animationFrameHandle = requestAnimationFrame(animate)

    return () => {
      cancelled = true
      cancelAnimationFrame(animationFrameHandle)
    }
  }, [])

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
