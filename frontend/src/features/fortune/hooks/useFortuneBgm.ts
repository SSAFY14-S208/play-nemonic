import { useCallback, useEffect, useRef, useState } from 'react'

import { FORTUNE_SOUND_PATHS } from '..'

const FORTUNE_BGM_VOLUME = 0.24

export function useFortuneBgm() {
  const [isBgmMuted, setIsBgmMuted] = useState(false)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const hasPlaybackStartedRef = useRef(false)
  const isBgmMutedRef = useRef(false)
  const tryStartBgmRef = useRef<() => void>(() => undefined)

  const toggleFortuneBgmMuted = useCallback(() => {
    const nextIsBgmMuted = !isBgmMutedRef.current
    const currentAudio = audioRef.current

    isBgmMutedRef.current = nextIsBgmMuted
    setIsBgmMuted(nextIsBgmMuted)

    if (currentAudio) {
      currentAudio.muted = nextIsBgmMuted
    }

    if (!nextIsBgmMuted) {
      tryStartBgmRef.current()
    }
  }, [])

  useEffect(() => {
    if (typeof window === 'undefined') {
      return undefined
    }

    let cancelled = false
    const audio = new Audio(FORTUNE_SOUND_PATHS.bgm)
    audio.loop = true
    audio.muted = isBgmMutedRef.current
    audio.preload = 'auto'
    audio.volume = FORTUNE_BGM_VOLUME
    audioRef.current = audio

    const tryStartBgm = () => {
      const currentAudio = audioRef.current

      if (hasPlaybackStartedRef.current || !currentAudio || currentAudio.muted) {
        return
      }

      void currentAudio
        .play()
        .then(() => {
          if (cancelled) {
            return
          }

          hasPlaybackStartedRef.current = true
          removeStartListeners()
        })
        .catch(() => undefined)
    }

    tryStartBgmRef.current = tryStartBgm

    const handleVisibilityChange = () => {
      const currentAudio = audioRef.current

      if (!currentAudio || !hasPlaybackStartedRef.current) {
        return
      }

      if (document.hidden) {
        currentAudio.pause()
        return
      }

      if (currentAudio.muted) {
        return
      }

      void currentAudio.play().catch(() => undefined)
    }

    function addStartListeners() {
      window.addEventListener('pointerdown', tryStartBgm)
      window.addEventListener('keydown', tryStartBgm)
      window.addEventListener('touchstart', tryStartBgm)
    }

    function removeStartListeners() {
      window.removeEventListener('pointerdown', tryStartBgm)
      window.removeEventListener('keydown', tryStartBgm)
      window.removeEventListener('touchstart', tryStartBgm)
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
      tryStartBgmRef.current = () => undefined
    }
  }, [])

  return { isBgmMuted, toggleFortuneBgmMuted }
}
