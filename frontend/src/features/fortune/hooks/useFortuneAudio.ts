import { FORTUNE_SOUND_PATHS } from '../constants'

export function useFortuneAudio() {
  const playPrintStart = () => {
    playSound(FORTUNE_SOUND_PATHS.print, 0.36)
  }

  const playPrintComplete = () => {
    playSound(FORTUNE_SOUND_PATHS.cut, 0.42)
  }

  return {
    playPrintStart,
    playPrintComplete,
  }
}

function playSound(path: string, volume: number) {
  if (typeof window === 'undefined') {
    return
  }

  const audio = new Audio(path)
  audio.volume = volume
  void audio.play().catch(() => undefined)
}
