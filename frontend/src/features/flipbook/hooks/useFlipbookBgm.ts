import { useCallback, useEffect, useRef, useState } from 'react'

const FLIPBOOK_BGM_VOLUME = 0.18
const LEGACY_FLIPBOOK_ENTRANCE_BGM_WINDOW_KEY = '__flipbookEntranceBgmAudio'

type LegacyFlipbookEntranceAudioWindow = Window & {
  [LEGACY_FLIPBOOK_ENTRANCE_BGM_WINDOW_KEY]?: HTMLAudioElement | null
}

function resetFlipbookBgmAudio(audio: HTMLAudioElement) {
  audio.pause()
  audio.currentTime = 0
  audio.load()
}

function stopLegacyEntranceAudio() {
  const audioWindow = window as LegacyFlipbookEntranceAudioWindow
  const legacyAudio = audioWindow[LEGACY_FLIPBOOK_ENTRANCE_BGM_WINDOW_KEY]

  if (!legacyAudio) {
    return
  }

  legacyAudio.pause()
  legacyAudio.muted = true
  legacyAudio.currentTime = 0
  legacyAudio.removeAttribute('src')
  legacyAudio.load()
  audioWindow[LEGACY_FLIPBOOK_ENTRANCE_BGM_WINDOW_KEY] = null
}

export function useFlipbookBgm({
  shouldStart,
}: {
  shouldStart: boolean
}) {
  const [isBgmMuted, setIsBgmMuted] = useState(false)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const hasAttemptedInitialPlaybackRef = useRef(false)
  const isBgmMutedRef = useRef(false)

  const tryPlayFlipbookBgm = useCallback(
    ({
      restart,
    }: {
      restart: boolean
    }) => {
      const currentAudio = audioRef.current

      if (!currentAudio || isBgmMutedRef.current) {
        return
      }

      currentAudio.muted = false
      currentAudio.volume = FLIPBOOK_BGM_VOLUME

      if (restart) {
        currentAudio.currentTime = 0
      }

      void currentAudio.play().catch(() => undefined)
    },
    [],
  )

  const toggleFlipbookBgmMuted = useCallback(() => {
    const nextIsBgmMuted = !isBgmMutedRef.current
    const currentAudio = audioRef.current

    isBgmMutedRef.current = nextIsBgmMuted
    setIsBgmMuted(nextIsBgmMuted)

    if (!currentAudio) {
      return
    }

    currentAudio.muted = nextIsBgmMuted

    if (nextIsBgmMuted) {
      currentAudio.pause()
      return
    }

    tryPlayFlipbookBgm({ restart: false })
  }, [tryPlayFlipbookBgm])

  useEffect(() => {
    const currentAudio = audioRef.current

    if (!currentAudio) {
      return undefined
    }

    stopLegacyEntranceAudio()
    currentAudio.loop = true
    currentAudio.muted = isBgmMutedRef.current
    currentAudio.preload = 'auto'
    currentAudio.volume = FLIPBOOK_BGM_VOLUME
    resetFlipbookBgmAudio(currentAudio)

    const handleVisibilityChange = () => {
      const visibleAudio = audioRef.current

      if (!visibleAudio || isBgmMutedRef.current) {
        return
      }

      if (document.hidden) {
        visibleAudio.pause()
        return
      }

      void visibleAudio.play().catch(() => undefined)
    }

    const handlePageExit = () => {
      resetFlipbookBgmAudio(currentAudio)
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)
    window.addEventListener('pagehide', handlePageExit)
    window.addEventListener('beforeunload', handlePageExit)

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      window.removeEventListener('pagehide', handlePageExit)
      window.removeEventListener('beforeunload', handlePageExit)
      resetFlipbookBgmAudio(currentAudio)
      hasAttemptedInitialPlaybackRef.current = false
    }
  }, [])

  useEffect(() => {
    if (!shouldStart || hasAttemptedInitialPlaybackRef.current || isBgmMutedRef.current) {
      return undefined
    }

    hasAttemptedInitialPlaybackRef.current = true
    tryPlayFlipbookBgm({ restart: true })

    const retryInitialPlayback = () => {
      tryPlayFlipbookBgm({ restart: false })
    }

    window.addEventListener('pointerdown', retryInitialPlayback)
    window.addEventListener('keydown', retryInitialPlayback)
    window.addEventListener('touchstart', retryInitialPlayback)

    return () => {
      window.removeEventListener('pointerdown', retryInitialPlayback)
      window.removeEventListener('keydown', retryInitialPlayback)
      window.removeEventListener('touchstart', retryInitialPlayback)
    }
  }, [shouldStart, tryPlayFlipbookBgm])

  return {
    audioRef,
    isBgmMuted,
    toggleFlipbookBgmMuted,
  }
}
