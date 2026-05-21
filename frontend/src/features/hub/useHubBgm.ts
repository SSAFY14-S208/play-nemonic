import { useCallback, useEffect, useRef, useState } from 'react'

const HUB_BGM_PATH = '/sounds/hub/pastel-puzzle-room.mp3'
const HUB_BGM_VOLUME = 0.22

type UseHubBgmOptions = {
  disabled?: boolean
}

export function useHubBgm({ disabled = false }: UseHubBgmOptions = {}) {
  const [isBgmEnabled, setIsBgmEnabled] = useState(!disabled)
  const [isBgmPlaying, setIsBgmPlaying] = useState(false)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const hasPlaybackStartedRef = useRef(false)
  const isBgmEnabledRef = useRef(!disabled)
  const pauseHubBgmRef = useRef<() => void>(() => undefined)
  const tryStartHubBgmRef = useRef<() => void>(() => undefined)

  const toggleHubBgm = useCallback(() => {
    if (disabled) {
      return
    }

    const nextIsBgmEnabled = !isBgmEnabledRef.current

    isBgmEnabledRef.current = nextIsBgmEnabled
    setIsBgmEnabled(nextIsBgmEnabled)

    if (nextIsBgmEnabled) {
      tryStartHubBgmRef.current()
      return
    }

    pauseHubBgmRef.current()
  }, [disabled])

  useEffect(() => {
    if (typeof window === 'undefined' || disabled) {
      isBgmEnabledRef.current = false
      pauseHubBgmRef.current = () => undefined
      tryStartHubBgmRef.current = () => undefined

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
  }, [disabled])

  return {
    isBgmEnabled: disabled ? false : isBgmEnabled,
    isBgmPlaying: disabled ? false : isBgmPlaying,
    toggleHubBgm,
  }
}
