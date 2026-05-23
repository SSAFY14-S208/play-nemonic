import { playBrowserAudio, preloadBrowserAudio } from '@/shared/utils'
import { FORTUNE_SOUND_PATHS } from '..'

export function useFortuneAudio() {
  const preloadPrintStart = () => {
    preloadBrowserAudio(FORTUNE_SOUND_PATHS.print, 0.36)
  }

  const playPrintStart = () => {
    playSound(FORTUNE_SOUND_PATHS.print, 0.36)
  }

  const playPrintComplete = () => {
    playSound(FORTUNE_SOUND_PATHS.cut, 0.42)
  }

  const playTap = () => {
    playSound(FORTUNE_SOUND_PATHS.tap, 0.32)
  }

  return {
    preloadPrintStart,
    playPrintStart,
    playPrintComplete,
    playTap,
  }
}

function playSound(path: string, volume: number) {
  if (typeof window === 'undefined') {
    return
  }

  playBrowserAudio(path, volume)
}
