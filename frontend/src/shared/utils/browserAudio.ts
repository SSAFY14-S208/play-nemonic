type BrowserAudioEntry = {
  element: HTMLAudioElement
  decodedBuffer?: AudioBuffer
  decodePromise?: Promise<AudioBuffer | null>
}

type BrowserAudioWindow = Window & {
  webkitAudioContext?: typeof AudioContext
}

const browserAudioEntries = new Map<string, BrowserAudioEntry>()
let browserAudioContext: AudioContext | null = null

function getBrowserAudioContext() {
  if (typeof window === 'undefined') return null
  if (browserAudioContext) return browserAudioContext

  const AudioContextConstructor =
    window.AudioContext || (window as BrowserAudioWindow).webkitAudioContext
  if (!AudioContextConstructor) return null

  browserAudioContext = new AudioContextConstructor()
  return browserAudioContext
}

function getBrowserAudioEntry(soundPath: string, volume: number) {
  const cachedEntry = browserAudioEntries.get(soundPath)
  if (cachedEntry) {
    cachedEntry.element.volume = volume
    return cachedEntry
  }

  const audio = new Audio(soundPath)
  audio.volume = volume
  audio.preload = 'auto'
  audio.load()

  const entry: BrowserAudioEntry = {
    element: audio,
  }
  browserAudioEntries.set(soundPath, entry)

  return entry
}

export function preloadBrowserAudio(soundPath: string, volume: number) {
  if (typeof window === 'undefined') return

  const entry = getBrowserAudioEntry(soundPath, volume)
  if (entry.decodedBuffer || entry.decodePromise) return entry.element

  const audioContext = getBrowserAudioContext()
  if (!audioContext) return entry.element

  entry.decodePromise = fetch(soundPath)
    .then((response) => response.arrayBuffer())
    .then((arrayBuffer) => audioContext.decodeAudioData(arrayBuffer))
    .then((decodedBuffer) => {
      entry.decodedBuffer = decodedBuffer
      return decodedBuffer
    })
    .catch(() => null)

  return entry.element
}

export function playBrowserAudio(
  soundPath: string,
  volume: number,
  startOffsetSeconds = 0,
) {
  if (typeof window === 'undefined') return

  preloadBrowserAudio(soundPath, volume)
  const entry = browserAudioEntries.get(soundPath)
  if (!entry) return

  if (playDecodedBrowserAudio(entry, volume, startOffsetSeconds)) {
    return
  }

  const playableAudio =
    entry.element.paused || entry.element.ended
      ? entry.element
      : entry.element.cloneNode(true) as HTMLAudioElement
  playableAudio.volume = volume

  if (startOffsetSeconds > 0) {
    try {
      playableAudio.currentTime = startOffsetSeconds
    } catch {
      // Some browsers make cloned audio seekable only after metadata is ready.
    }
  }

  void playableAudio.play().catch(() => undefined)
}

function playDecodedBrowserAudio(
  entry: BrowserAudioEntry,
  volume: number,
  startOffsetSeconds: number,
) {
  const audioContext = getBrowserAudioContext()
  const decodedBuffer = entry.decodedBuffer
  if (!audioContext || !decodedBuffer) return false

  if (audioContext.state === 'suspended') {
    void audioContext.resume().catch(() => undefined)
  }

  const source = audioContext.createBufferSource()
  const gain = audioContext.createGain()
  source.buffer = decodedBuffer
  gain.gain.value = volume
  source.connect(gain)
  gain.connect(audioContext.destination)

  const offsetSeconds = Math.min(
    Math.max(startOffsetSeconds, 0),
    Math.max(decodedBuffer.duration - 0.01, 0),
  )
  source.start(0, offsetSeconds)

  return true
}
