import type { FortuneBirthInfo } from './types'

export const FORTUNE_STORAGE_KEY = 'nemonic-world:today-fortune'
export const FORTUNE_RESET_QUERY_PARAM = 'resetFortune'

export const FORTUNE_USER_NOT_READY_ERROR = 'USER_NOT_READY'
export const FORTUNE_NOON_FALLBACK_BIRTH_TIME = '12:00:00'

export const FORTUNE_EMPTY_BIRTH_INFO: FortuneBirthInfo = {
  birthDate: '',
  birthTime: '',
  calendarType: 'solar',
  timeUnknown: false,
}

export const FORTUNE_PRINT_DURATION_SECONDS = 4.2
export const FORTUNE_REDUCED_MOTION_DURATION_SECONDS = 0.9
export const FORTUNE_PRINT_FALLBACK_TIMEOUT_SECONDS = 14
export const FORTUNE_PRINT_VIDEO_PATH = '/videos/fortune/printing-aura.mp4'

export const FORTUNE_SOUND_PATHS = {
  print: '/sounds/print_label.mp3',
  cut: '/sounds/cut_label.mp3',
  bgm: '/sounds/fortune/moonlit-tarot-shelf.mp3',
  tap: '/sounds/fortune/tap.mp3',
} as const

export const FORTUNE_SCORE_LABELS = [
  { key: 'overall', label: '종합운' },
  { key: 'love', label: '관계운' },
  { key: 'work', label: '일운' },
  { key: 'money', label: '금전운' },
] as const

export const FORTUNE_POSTIT_LINES = [
  '오늘은 정리할수록 운이 열린다',
  '작은 친절이 큰 기회를 데려온다',
  '천천히 고르면 좋은 답이 보인다',
  '낯선 제안 속에 힌트가 숨어 있다',
  '먼저 웃는 사람이 흐름을 바꾼다',
] as const

export const FORTUNE_TITLES = [
  '차분한 빛이 모이는 하루',
  '가볍게 시작하면 풀리는 하루',
  '다정한 연결이 반짝이는 하루',
  '집중한 만큼 길이 나는 하루',
  '새로운 문장이 필요한 하루',
] as const

export const FORTUNE_KEYWORDS = ['정리', '연결', '집중', '균형', '시작'] as const

export const FORTUNE_LUCKY_COLORS = [
  { name: '라벤더 밀크', hex: '#cdb7f6' },
  { name: '민트 포그', hex: '#9ed8c3' },
  { name: '버터 옐로', hex: '#efd27b' },
  { name: '코랄 핑크', hex: '#ef9aa7' },
  { name: '스카이 블루', hex: '#91bde8' },
] as const
