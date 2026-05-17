import { useCallback, useEffect, useRef, useState } from 'react'
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

function getHubLoadingStatusText(displayProgress: number) {
  if (displayProgress < 30) return '네모닉 룸의 불을 켜고 있어요'
  if (displayProgress < 60) return '메모지들이 하나둘 깨어나는 중이에요'
  if (displayProgress < 90) return '오늘의 놀이를 방 안에 배치하고 있어요'

  return '거의 다 왔어요, 마지막 스티커를 붙이는 중이에요'
}

const HUB_LOADING_SUBTITLE_PENDING = '오늘은 어떤 놀이가 기다릴까요?'
const HUB_LOADING_SUBTITLE_READY = '재미있는 것들이 가득해요'

export function useHubLoadingOverlay(isCanvasReady: boolean) {
  const { active, progress } = useProgress()
  const [displayProgress, setDisplayProgress] = useState(0)
  const [hasEnteredHub, setHasEnteredHub] = useState(false)
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

  useEffect(() => {
    visibleStartedAtRef.current = performance.now()
  }, [])

  useEffect(() => {
    targetProgressRef.current = Math.max(
      targetProgressRef.current,
      targetProgress,
    )
  }, [targetProgress])

  useEffect(() => {
    if (!active) return

    hasStartedLoadingRef.current = true
  }, [active])

  useEffect(() => {
    let cancelled = false
    let animationFrameHandle = 0
    let lastTimestamp: number | null = null
    let internalProgress = 0

    const animate = (timestamp: number) => {
      if (cancelled) return

      if (lastTimestamp !== null) {
        const deltaSeconds = (timestamp - lastTimestamp) / 1000
        const target = targetProgressRef.current

        if (internalProgress < target) {
          const remaining = target - internalProgress
          const speed = Math.max(remaining * 2, 12)
          const step = Math.min(speed * deltaSeconds, remaining)
          internalProgress += step

          setDisplayProgress((current) =>
            Math.round(current) === Math.round(internalProgress)
              ? current
              : internalProgress,
          )
        }
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
    if (!isReady || !hasEnteredHub) return

    const hideTimerId = window.setTimeout(() => {
      setIsVisible(false)
    }, HUB_LOADING_READY_HOLD_MS)

    return () => {
      window.clearTimeout(hideTimerId)
    }
  }, [hasEnteredHub, isReady])

  const enterHub = useCallback(() => {
    if (!isReady) return

    setHasEnteredHub(true)
  }, [isReady])

  return {
    displayProgress: Math.round(displayProgress),
    enterHub,
    hasEnteredHub,
    isReady,
    isVisible,
    statusText: getHubLoadingStatusText(displayProgress),
    subtitleText: isReady
      ? HUB_LOADING_SUBTITLE_READY
      : HUB_LOADING_SUBTITLE_PENDING,
  }
}
