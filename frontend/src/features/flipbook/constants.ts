import type { HowToPlayPanel } from '@/shared/components'
import type { DrawingBoardSize } from '@/shared/types'

import type { FlipbookStep } from './types'

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

export const FLIPBOOK_ROOM_CODE = 'ABC123'
export const FLIPBOOK_TOPIC = '동물원에 간 우주비행사'
export const FLIPBOOK_BACKGROUND_COLOR = '#ffffff'

export const FLIPBOOK_BOARD_SIZE: DrawingBoardSize = {
  width: 680,
  height: 520,
}

export const FLIPBOOK_HOW_TO_PLAY_PANELS: HowToPlayPanel[] = [
  {
    id: 'gather',
    title: '2~12명이 모여요',
    description: '방 코드를 친구에게 공유해 한 방에 모입니다.',
  },
  {
    id: 'topic',
    title: '주제를 확인해요',
    description: '모두 같은 주제를 받고, 주제에 맞는 그림을 그릴 준비를 해요.',
  },
  {
    id: 'draw',
    title: '프레임을 그려요',
    description: '제한 시간 안에 이전 프레임을 이어 그려 움직임을 만들어요.',
  },
  {
    id: 'reveal',
    title: '플립북 완성!',
    description: '모든 프레임이 모이면 한 편의 플립북 애니메이션이 완성돼요.',
  },
]
