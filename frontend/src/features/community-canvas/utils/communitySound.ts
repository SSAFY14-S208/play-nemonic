const COMMUNITY_MEMO_ATTACH_SOUND_PATH = '/sounds/community/memo-attach-smooth-wall.m4a'
const COMMUNITY_MEMO_DETACH_SOUND_PATH = '/sounds/community/memo-detach-smooth-wall.m4a'
const COMMUNITY_MEMO_ATTACH_SOUND_VOLUME = 0.85
const COMMUNITY_MEMO_DETACH_SOUND_VOLUME = 0.85
const COMMUNITY_MEMO_DETACH_SOUND_START_OFFSET_SECONDS = 0.25

const communitySoundTemplates = new Map<string, HTMLAudioElement>()

export function preloadCommunityMemoSounds() {
  preloadCommunitySound(COMMUNITY_MEMO_ATTACH_SOUND_PATH, COMMUNITY_MEMO_ATTACH_SOUND_VOLUME)
  preloadCommunitySound(COMMUNITY_MEMO_DETACH_SOUND_PATH, COMMUNITY_MEMO_DETACH_SOUND_VOLUME)
}

export function playCommunityMemoAttachSound() {
  playCommunitySound(COMMUNITY_MEMO_ATTACH_SOUND_PATH, COMMUNITY_MEMO_ATTACH_SOUND_VOLUME, 0)
}

export function playCommunityMemoDetachSound() {
  playCommunitySound(
    COMMUNITY_MEMO_DETACH_SOUND_PATH,
    COMMUNITY_MEMO_DETACH_SOUND_VOLUME,
    COMMUNITY_MEMO_DETACH_SOUND_START_OFFSET_SECONDS,
  )
}

function preloadCommunitySound(soundPath: string, volume: number) {
  if (typeof window === 'undefined') return

  const cachedAudio = communitySoundTemplates.get(soundPath)
  if (cachedAudio) return cachedAudio

  const audio = new Audio(soundPath)
  audio.volume = volume
  audio.preload = 'auto'
  audio.load()
  communitySoundTemplates.set(soundPath, audio)

  return audio
}

function playCommunitySound(soundPath: string, volume: number, startOffsetSeconds: number) {
  const templateAudio = preloadCommunitySound(soundPath, volume)
  if (!templateAudio) return

  const playableAudio = templateAudio.cloneNode(true) as HTMLAudioElement
  playableAudio.volume = volume
  if (startOffsetSeconds > 0) {
    try {
      playableAudio.currentTime = startOffsetSeconds
    } catch {
      // If the browser has not made the cloned audio seekable yet, fall back to normal playback.
    }
  }
  void playableAudio.play().catch(() => undefined)
}
