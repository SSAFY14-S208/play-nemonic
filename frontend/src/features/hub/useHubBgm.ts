import { useCallback, useEffect, useRef, useState } from 'react'

const HUB_BGM_PATH = '/sounds/hub/pastel-puzzle-room.mp3'
const HUB_BGM_VOLUME = 0.22

export function useHubBgm() {
  const [isBgmEnabled, setIsBgmEnabled] = useState(true)
  const [isBgmPlaying, setIsBgmPlaying] = useState(false)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const hasPlaybackStartedRef = useRef(false)
  const isBgmEnabledRef = useRef(true)
  const pauseHubBgmRef = useRef<() => void>(() => undefined)
  const tryStartHubBgmRef = useRef<() => void>(() => undefined)

  const toggleHubBgm = useCallback(() => {
    const nextIsBgmEnabled = !isBgmEnabledRef.current

    isBgmEnabledRef.current = nextIsBgmEnabled
    setIsBgmEnabled(nextIsBgmEnabled)

    if (nextIsBgmEnabled) {
      tryStartHubBgmRef.current()
      return
    }

    pauseHubBgmRef.current()
  }, [])

  useEffect(() => {
    if (typeof window === 'undefined') {
      return undefined
    }

    let cancelled = false
    const audio = new Audio(HUB_BGM_PATH)
    audio.loop = true
    audio.preload = 'auto'
    audio.volume = HUB_BGM_VOLUME
    audioRef.current = audio

    const pauseHubBgm = () => {
      const currentAudio = audioRef.current

      if (!currentAudio) {
        return
      }

      currentAudio.pause()

      if (!cancelled) {
        setIsBgmPlaying(false)
      }
    }

    const tryStartHubBgm = () => {
      const currentAudio = audioRef.current

      if (!currentAudio || !isBgmEnabledRef.current || document.hidden) {
        return
      }

      currentAudio.muted = false
      currentAudio.volume = HUB_BGM_VOLUME

      void currentAudio
        .play()
        .then(() => {
          if (cancelled) {
            return
          }

          hasPlaybackStartedRef.current = true
          setIsBgmPlaying(true)
          removeStartListeners()
        })
        .catch(() => {
          if (!cancelled) {
            setIsBgmPlaying(false)
          }
        })
    }

    pauseHubBgmRef.current = pauseHubBgm
    tryStartHubBgmRef.current = tryStartHubBgm

    const handleVisibilityChange = () => {
      const currentAudio = audioRef.current

      if (!currentAudio || !hasPlaybackStartedRef.current) {
        return
      }

      if (document.hidden) {
        pauseHubBgm()
        return
      }

      if (isBgmEnabledRef.current) {
        tryStartHubBgm()
      }
    }

    function addStartListeners() {
      window.addEventListener('pointerdown', tryStartHubBgm)
      window.addEventListener('keydown', tryStartHubBgm)
      window.addEventListener('touchstart', tryStartHubBgm)
    }

    function removeStartListeners() {
      window.removeEventListener('pointerdown', tryStartHubBgm)
      window.removeEventListener('keydown', tryStartHubBgm)
      window.removeEventListener('touchstart', tryStartHubBgm)
    }

    addStartListeners()
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      cancelled = true
      removeStartListeners()
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      audio.pause()
      audio.removeAttribute('src')
      audio.load()
      audioRef.current = null
      hasPlaybackStartedRef.current = false
      pauseHubBgmRef.current = () => undefined
      tryStartHubBgmRef.current = () => undefined
    }
  }, [])

  return {
    isBgmEnabled,
    isBgmPlaying,
    toggleHubBgm,
  }
}
