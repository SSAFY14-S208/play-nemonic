import type { HowToPlayPanel, HowToPlayVisualImage } from '@/shared/components'
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

export const FLIPBOOK_SOUND_PATHS = {
  entranceBgm: '/sounds/flipbook/entrance-bgm.mp3',
  print: '/sounds/print_label.mp3',
  cut: '/sounds/cut_label.mp3',
} as const

export const FLIPBOOK_BOARD_SIZE: DrawingBoardSize = {
  width: 680,
  height: 520,
}

const FLIPBOOK_EXAMPLE_FRAME_NUMBERS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] as const

const FLIPBOOK_EXAMPLE_FRAMES: HowToPlayVisualImage[] = FLIPBOOK_EXAMPLE_FRAME_NUMBERS.map(
  (frameNumberValue) => {
    const frameNumber = String(frameNumberValue).padStart(2, '0')

    return {
      src: `/images/flipbook-entrance/${frameNumber}.webp`,
      alt: `네모닉 월드 플립북 애니메이션 ${frameNumberValue}번째 프레임 예시`,
      label: `${frameNumberValue}프레임`,
    }
  },
)

function getFlipbookExampleFrame(frameIndex: number): HowToPlayVisualImage {
  return (
    FLIPBOOK_EXAMPLE_FRAMES[frameIndex] ?? {
      src: '/images/flipbook-entrance/01.webp',
      alt: '네모닉 월드 플립북 애니메이션 1번째 프레임 예시',
      label: '1프레임',
    }
  )
}

const FLIPBOOK_EXAMPLE_FRAME_1 = getFlipbookExampleFrame(0)
const FLIPBOOK_EXAMPLE_FRAME_6 = getFlipbookExampleFrame(5)
const FLIPBOOK_FILMSTRIP_FRAMES = [0, 2, 4, 6, 8, 11].map(getFlipbookExampleFrame)
const FLIPBOOK_SMALL_MOTION_FRAMES = [3, 4, 5, 6, 7, 8].map(getFlipbookExampleFrame)

export const FLIPBOOK_HOW_TO_PLAY_PANELS: HowToPlayPanel[] = [
  {
    id: 'gather',
    eyebrow: 'STEP 1',
    title: '한 장면씩 나눠 그려요',
    description:
      '플립북은 여러 사람이 같은 캐릭터를 조금씩 움직여 그린 프레임을 이어 붙여 만드는 짧은 애니메이션이에요.',
    bullets: [
      '각 참여자는 자신에게 배정된 한 장면을 그려요.',
      '프레임 사이의 작은 차이가 모이면 움직임처럼 보여요.',
    ],
    visual: {
      type: 'filmstrip',
      images: FLIPBOOK_FILMSTRIP_FRAMES,
      caption: '이미 준비된 프레임 에셋처럼 장면이 조금씩 달라지는 것이 플립북의 재료예요.',
    },
  },
  {
    id: 'topic',
    eyebrow: 'STEP 2',
    title: '첫 프레임에서 기준을 잡아요',
    description:
      '처음 그린 프레임은 뒤에 이어질 움직임의 기준이 됩니다. 캐릭터의 크기와 위치가 너무 작지 않게 잡아주세요.',
    bullets: [
      '화면 안에서 움직일 대상이 잘 보이게 그려요.',
      '다음 사람이 따라 그릴 수 있도록 큰 형태를 분명하게 남겨요.',
    ],
    visual: {
      type: 'single',
      badge: '시작',
      frameLabel: '첫 프레임',
      image: FLIPBOOK_EXAMPLE_FRAME_1,
      caption: '첫 장면은 뒤에 이어질 움직임의 출발점이에요.',
    },
  },
  {
    id: 'draw',
    eyebrow: 'STEP 3',
    title: '힌트를 보고 다음 프레임을 그려요',
    description:
      '드로잉 화면에서는 이전 프레임이 흐릿한 힌트 이미지로 보여요. 그 위에 다음 동작을 살짝 바꿔 그리면 됩니다.',
    bullets: [
      '힌트의 윤곽을 기준으로 위치를 조금만 움직여요.',
      '표정, 입 모양, 몸 기울기처럼 한두 부분만 바꿔도 충분해요.',
    ],
    visual: {
      type: 'onion-skin',
      hintImage: FLIPBOOK_EXAMPLE_FRAME_6,
      hintLabel: '이전 프레임 힌트',
      instruction: '실제 게임처럼 앞 프레임이 흐릿한 힌트 이미지로 깔려요.',
      caption: '이 흐린 이미지를 기준으로 다음 장면의 위치와 움직임을 잡으면 돼요.',
    },
  },
  {
    id: 'onion-skin',
    eyebrow: 'STEP 4',
    title: '연속 프레임의 흐름을 만들어요',
    description:
      '플립북은 한 장 한 장이 빠르게 넘어가며 움직임이 됩니다. 이어지는 프레임들을 보면 장면이 어떤 방향으로 움직이는지 바로 이해할 수 있어요.',
    bullets: [
      '연속된 프레임에서 캐릭터와 소품의 위치가 어떻게 바뀌는지 확인해요.',
      '움직일 부분만 살짝 바꾸면 gif처럼 부드럽게 이어져요.',
    ],
    visual: {
      type: 'filmstrip',
      images: FLIPBOOK_SMALL_MOTION_FRAMES,
      caption: '가까운 프레임 6장을 나란히 보면 작은 변화가 움직임으로 이어지는 흐름이 보여요.',
    },
  },
  {
    id: 'reveal',
    eyebrow: 'FINISH',
    title: '결과는 애니메이션처럼 재생돼요',
    description:
      '모든 프레임이 모이면 결과 화면에서 순서대로 재생됩니다. 정지된 그림 여러 장이 gif처럼 움직이는 장면으로 바뀌어요.',
    bullets: [
      '결과 화면에서는 프레임이 자동으로 넘어가요.',
      '완성된 플립북은 다시 보거나 저장해 공유할 수 있어요.',
    ],
    visual: {
      type: 'animation',
      images: FLIPBOOK_EXAMPLE_FRAMES,
      frameIntervalMs: 170,
      caption: '준비된 12장 프레임이 빠르게 넘어가며 최종 결과물이 애니메이션처럼 재생돼요.',
    },
  },
]
