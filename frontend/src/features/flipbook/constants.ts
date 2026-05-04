import type { DrawingBoardSize } from '@/shared/types'

export type FlipbookStep = 'booth' | 'lobby' | 'drawing' | 'result'

export interface FlipbookParticipant {
  id: string
  name: string
  avatar: string
  isHost?: boolean
}

export const FLIPBOOK_STEPS: { key: FlipbookStep; label: string }[] = [
  { key: 'booth', label: '부스' },
  { key: 'lobby', label: '대기실' },
  { key: 'drawing', label: '드로잉' },
  { key: 'result', label: '결과' },
]

export const FLIPBOOK_PARTICIPANTS: FlipbookParticipant[] = [
  { id: 'fox', name: '여우 (나)', avatar: '🦊', isHost: true },
  { id: 'cat', name: '고양이', avatar: '🐱' },
  { id: 'bear', name: '곰돌이', avatar: '🐻' },
  { id: 'dog', name: '강아지', avatar: '🐶' },
  { id: 'rabbit', name: '토끼', avatar: '🐰' },
]

export const FLIPBOOK_COLORS = [
  '#8b1e2d',
  '#ff3d00',
  '#ffc629',
  '#63c086',
  '#75a7df',
  '#3f78c7',
  '#9b6bd3',
  '#ffa3c3',
  '#c16535',
  '#ffffff',
  '#9a9ca3',
]

export const FLIPBOOK_TIME_LIMITS_SECONDS = [30, 45, 60]
export const FLIPBOOK_ROOM_CODE = 'ABC123'
export const FLIPBOOK_TOPIC = '동물원에 간 우주비행사'
export const FLIPBOOK_BACKGROUND_COLOR = '#fffdf7'
export const FLIPBOOK_MIN_FRAME_COUNT = 8

export const FLIPBOOK_BOARD_SIZE: DrawingBoardSize = {
  width: 850,
  height: 720,
}

export function getMinimumRoundCount(participantCount: number) {
  return Math.max(1, Math.ceil(FLIPBOOK_MIN_FRAME_COUNT / participantCount))
}
