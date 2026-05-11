import type { DrawingBoardSize } from '@/shared/types'
import type { FlipbookStep, FlipbookTimeLimitSeconds } from './types'

export const FLIPBOOK_STEPS: { key: FlipbookStep; label: string }[] = [
  { key: 'booth', label: '부스' },
  { key: 'lobby', label: '대기실' },
  { key: 'drawing', label: '드로잉' },
  { key: 'result', label: '결과' },
]

export const FLIPBOOK_STEP_PATHS = {
  booth: '/flipbook',
  lobby: '/flipbook/lobby',
  drawing: '/flipbook/drawing',
  result: '/flipbook/result',
} as const satisfies Record<FlipbookStep, string>

export function getFlipbookStepPath(step: FlipbookStep) {
  return FLIPBOOK_STEP_PATHS[step]
}

export function getFlipbookStepFromPathname(pathname: string): FlipbookStep {
  const matchedStep = Object.entries(FLIPBOOK_STEP_PATHS).find(
    ([, stepPath]) => pathname === stepPath,
  )?.[0]

  if (
    matchedStep === 'booth' ||
    matchedStep === 'lobby' ||
    matchedStep === 'drawing' ||
    matchedStep === 'result'
  ) {
    return matchedStep
  }

  return 'booth'
}

export const FLIPBOOK_COLORS = [
  '#1a1a1a',
  '#5a5a5a',
  '#1f4ec5',
  '#ffffff',
  '#9d9d9d',
  '#5fb1e0',
  '#0c8a3e',
  '#a31f1f',
  '#7a3a1f',
  '#1bb750',
  '#e74040',
  '#f08c2a',
  '#a35a25',
  '#a51b54',
  '#d6877a',
  '#fcc23a',
  '#f33fa6',
  '#fcd0c5',
]

export const FLIPBOOK_TIME_LIMITS_SECONDS = [30, 45, 60] as const satisfies readonly FlipbookTimeLimitSeconds[]
export const FLIPBOOK_ROOM_CODE = 'ABC123'
export const FLIPBOOK_TOPIC = '동물원에 간 우주비행사'
export const FLIPBOOK_BACKGROUND_COLOR = '#ffffff'

export const FLIPBOOK_BOARD_SIZE: DrawingBoardSize = {
  width: 680,
  height: 520,
}
