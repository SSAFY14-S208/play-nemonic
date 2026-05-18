import { useCallback, useEffect, useRef, useState } from 'react'
import { useProgress } from '@react-three/drei'
import { useCanvasPauseStore } from '@/shared/stores'

const BASE_FILL_DURATION_SECONDS = 8
const BASE_FILL_DURATION_MS = BASE_FILL_DURATION_SECONDS * 1000
const PRE_CANVAS_READY_CAP_PERCENT = 92

// Pop sequence after the bar hits 100%. Must stay in sync with the motion
// transitions in HubLoadingOverlay.tsx (PERCENT_FADE_OUT + BAR_POP).
export const PERCENT_FADE_OUT_DURATION_MS = 250
export const BAR_POP_DURATION_MS = 500
const POP_SEQUENCE_DURATION_MS =
  PERCENT_FADE_OUT_DURATION_MS + BAR_POP_DURATION_MS

export const HUB_ROOM_REVEAL_START_DELAY_MS = 260
export const HUB_ROOM_REVEAL_DURATION_MS = 1700
const HUB_LOADING_CACHE_FALLBACK_MS = 1500
const HUB_ENTRY_CONFIRMED_STORAGE_KEY = 'play-nemonic:hub-entry-confirmed'

function wait(durationMs: number) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, durationMs)
  })
}

function getHubLoadingStatusText(displayProgress: number) {
  if (displayProgress < 30) return '네모닉 룸의 불을 켜고 있어요'
  if (displayProgress < 60) return '메모지들이 하나둘 깨어나는 중이에요'
  if (displayProgress < 90) return '오늘의 놀이를 방 안에 배치하고 있어요'

  return '거의 다 왔어요, 마지막 스티커를 붙이는 중이에요'
}

const HUB_LOADING_SUBTITLE_PENDING = '오늘은 어떤 놀이가 기다릴까요?'
const HUB_LOADING_SUBTITLE_READY = '재미있는 것들이 가득해요'
const HUB_LOADING_SUBTITLE_ENTERING = '방이 천천히 열리고 있어요'

function readHubEntryConfirmedInCurrentTab() {
  if (typeof window === 'undefined') return false

  try {
    return (
      window.sessionStorage.getItem(HUB_ENTRY_CONFIRMED_STORAGE_KEY) === 'true'
    )
  } catch {
    return false
  }
}

function saveHubEntryConfirmedInCurrentTab() {
  try {
    window.sessionStorage.setItem(HUB_ENTRY_CONFIRMED_STORAGE_KEY, 'true')
  } catch {
    // Session storage can be unavailable in restricted browser modes.
  }
}

export function useHubLoadingOverlay(isCanvasReady: boolean) {
  const { active, progress } = useProgress()
  const [hasConfirmedHubEntry, setHasConfirmedHubEntry] = useState(false)
  const [isReady, setIsReady] = useState(false)
  const [isVisible, setIsVisible] = useState(true)
  const [isRevealingRoom, setIsRevealingRoom] = useState(false)
  const [hasReachedFull, setHasReachedFull] = useState(false)
  const [statusText, setStatusText] = useState(() => getHubLoadingStatusText(0))
  const hasStartedLoadingRef = useRef(false)
  const hasReachedFullRef = useRef(false)
  const targetProgressRef = useRef(0)

  // Imperative refs — the bar fill is driven by a Web Animations API animation
  // running on the compositor thread (immune to main-thread blockage), and
  // the percent text is updated via textContent. React state only carries
  // milestone-level status text and the one-shot pop flag.
  const barFillRef = useRef<HTMLDivElement | null>(null)
  const percentTextRef = useRef<HTMLSpanElement | null>(null)

  const targetProgress = isCanvasReady
    ? Math.max(progress, 0)
    : Math.min(progress, PRE_CANVAS_READY_CAP_PERCENT)

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      await Promise.resolve()
      if (cancelled) return

      setHasConfirmedHubEntry(readHubEntryConfirmedInCurrentTab())
    })()

    return () => {
      cancelled = true
    }
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

  // Compositor-thread bar fill. The animation lives on the compositor, so the
  // bar stays buttery smooth even when the main thread is busy parsing GLB.
  // A lightweight rAF control loop toggles play/pause based on whether the
  // bar is behind the actual loading target — main-thread hiccups can only
  // delay the *control* check, never the visible motion.
  useEffect(() => {
    const barElement = barFillRef.current
    if (!barElement) return

    const fillAnimation = barElement.animate(
      [{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }],
      {
        duration: BASE_FILL_DURATION_MS,
        easing: 'linear',
        fill: 'forwards',
      },
    )
    fillAnimation.pause()

    let cancelled = false
    let rafId = 0
    let lastStatusText = getHubLoadingStatusText(0)
    let lastWrittenPercent = -1

    const tick = () => {
      if (cancelled || hasReachedFullRef.current) return

      const currentTimeMs =
        typeof fillAnimation.currentTime === 'number'
          ? fillAnimation.currentTime
          : 0
      const barProgress = Math.min(
        100,
        (currentTimeMs / BASE_FILL_DURATION_MS) * 100,
      )
      const target = targetProgressRef.current

      // Play when bar is behind the loading target, pause when caught up.
      // No deadband — for target=100 we want the animation to run all the way
      // to currentTime=duration so it visually fills to 100%.
      if (barProgress < target) {
        const state = fillAnimation.playState
        if (state === 'paused' || state === 'idle') {
          fillAnimation.play()
        }
      } else if (
        fillAnimation.playState === 'running' &&
        target < 100
      ) {
        fillAnimation.pause()
      }

      const roundedPercent = Math.round(barProgress)
      if (
        percentTextRef.current &&
        roundedPercent !== lastWrittenPercent
      ) {
        percentTextRef.current.textContent = `${roundedPercent}%`
        lastWrittenPercent = roundedPercent
      }

      const nextStatusText = getHubLoadingStatusText(barProgress)
      if (nextStatusText !== lastStatusText) {
        lastStatusText = nextStatusText
        setStatusText(nextStatusText)
      }

      if (barProgress >= 99.95 && !hasReachedFullRef.current) {
        hasReachedFullRef.current = true
        setHasReachedFull(true)
        return
      }

      rafId = requestAnimationFrame(tick)
    }

    rafId = requestAnimationFrame(tick)

    return () => {
      cancelled = true
      cancelAnimationFrame(rafId)
      fillAnimation.cancel()
    }
  }, [])

  // Ready phase fires after the pop sequence (text fade-out → bar glow pulse)
  // completes. Sequencing here keeps the bar/button transition aligned with
  // the motion transitions defined in HubLoadingOverlay.
  useEffect(() => {
    if (!hasReachedFull || isReady) return

    let cancelled = false

    ;(async () => {
      await wait(POP_SEQUENCE_DURATION_MS)
      if (cancelled) return

      setIsReady(true)
    })()

    return () => {
      cancelled = true
    }
  }, [hasReachedFull, isReady])

  // Resume the R3F frameloop once the bar has filled and the pop has played.
  // HubLoader paused it on mount so the 60fps render couldn't compete with
  // the bar fill; from this point on the canvas runs normally.
  useEffect(() => {
    if (!isReady) return
    useCanvasPauseStore.getState().setPaused(false)
  }, [isReady])

  // Cache fallback: if no loading event ever fires (everything served from
  // the in-memory cache before useProgress could observe a load), nudge the
  // target to 100 so the paced fill can complete.
  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (!isCanvasReady) return

      await wait(HUB_LOADING_CACHE_FALLBACK_MS)
      if (cancelled || hasStartedLoadingRef.current) return

      targetProgressRef.current = Math.max(targetProgressRef.current, 100)
    })()

    return () => {
      cancelled = true
    }
  }, [isCanvasReady])

  const shouldShowPlayButton =
    isReady && !hasConfirmedHubEntry && !isRevealingRoom

  useEffect(() => {
    if (!isReady || !hasConfirmedHubEntry) return

    let cancelled = false

    ;(async () => {
      await wait(HUB_ROOM_REVEAL_START_DELAY_MS)
      if (cancelled) return

      setIsRevealingRoom(true)

      await wait(HUB_ROOM_REVEAL_DURATION_MS)
      if (cancelled) return

      setIsVisible(false)
    })()

    return () => {
      cancelled = true
    }
  }, [hasConfirmedHubEntry, isReady])

  const enterHub = useCallback(() => {
    if (!isReady) return

    saveHubEntryConfirmedInCurrentTab()
    setHasConfirmedHubEntry(true)
  }, [isReady])

  return {
    barFillRef,
    percentTextRef,
    enterHub,
    hasConfirmedHubEntry,
    hasReachedFull,
    isReady,
    isRevealingRoom,
    isVisible,
    shouldShowPlayButton,
    statusText,
    subtitleText: isReady
      ? hasConfirmedHubEntry
        ? HUB_LOADING_SUBTITLE_ENTERING
        : HUB_LOADING_SUBTITLE_READY
      : HUB_LOADING_SUBTITLE_PENDING,
  }
}
