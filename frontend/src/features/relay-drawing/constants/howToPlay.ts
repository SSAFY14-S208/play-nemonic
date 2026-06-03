import type { HowToPlayPanel, HowToPlayVisualImage } from '@/shared/components'

import {
  drawing2Body,
  drawing2Face,
  drawing2Leg,
} from '@/features/relay-drawing/assets'

const FACE_EXAMPLE: HowToPlayVisualImage = {
  src: drawing2Face,
  alt: '얼굴 프레임에 그려진 캐릭터 얼굴 예시',
  label: '얼굴',
}

const BODY_EXAMPLE: HowToPlayVisualImage = {
  src: drawing2Body,
  alt: '몸통 프레임에 이어 그린 캐릭터 몸 예시',
  label: '몸',
}

const LEGS_EXAMPLE: HowToPlayVisualImage = {
  src: drawing2Leg,
  alt: '다리 프레임에 이어 그린 캐릭터 다리 예시',
  label: '다리',
}

export const RELAY_HOW_TO_PLAY_PANELS: HowToPlayPanel[] = [
  {
    id: 'gather',
    eyebrow: 'STEP 1',
    title: '2~6명이 한 방에 모여요',
    description:
      '방을 만들고 코드를 공유하면 친구들이 같은 릴레이 드로잉 방에 입장할 수 있어요.',
    bullets: ['방장은 시작 버튼을 눌러 게임을 열어요.', '참여자는 순서대로 한 프레임씩 이어 그려요.'],
    visual: {
      type: 'room',
      roomCode: 'NEMO42',
      participants: ['나', '친구 1', '친구 2', '친구 3', '친구 4', '친구 5'],
      images: [FACE_EXAMPLE, BODY_EXAMPLE, LEGS_EXAMPLE],
      caption: '각자 다른 조각을 그려 하나의 캐릭터를 완성해요.',
    },
  },
  {
    id: 'face',
    eyebrow: 'ROUND 1',
    title: '얼굴 프레임부터 시작해요',
    description:
      '첫 번째 사람은 캐릭터의 얼굴을 그려요. 아래쪽 일부가 다음 사람에게 힌트로 넘어갑니다.',
    bullets: ['얼굴, 표정, 머리 모양을 자유롭게 그려요.', '목이나 어깨가 이어질 위치를 살짝 남겨두면 좋아요.'],
    visual: {
      type: 'single',
      badge: '1라운드',
      frameLabel: '얼굴 프레임 예시',
      image: FACE_EXAMPLE,
      caption: '이런 식으로 얼굴 영역 안에 캐릭터의 첫인상을 잡아줘요.',
    },
  },
  {
    id: 'body',
    eyebrow: 'ROUND 2',
    title: '몸은 힌트를 보고 이어 그려요',
    description:
      '다음 사람은 이전 그림 전체가 아니라 연결에 필요한 일부 힌트만 보고 몸을 이어 그립니다.',
    bullets: ['얼굴의 끝부분을 보고 목과 몸을 자연스럽게 연결해요.', '앞 사람이 어떤 캐릭터를 상상했을지 추측하는 재미가 있어요.'],
    visual: {
      type: 'drawing-hint',
      hintImage: FACE_EXAMPLE,
      resultImage: BODY_EXAMPLE,
      hintLabel: '이전 사람의 그림',
      instruction: '위쪽 힌트를 참고해 몸통을 이어 그리고, 아래 구간은 다음 힌트로 남겨요.',
      caption: '실제 게임에서는 이전 그림의 일부만 위에 보이고, 그 아래에서 몸을 이어 그려요.',
    },
  },
  {
    id: 'legs',
    eyebrow: 'ROUND 3',
    title: '마지막은 다리와 발을 완성해요',
    description:
      '몸통에서 내려오는 힌트를 보고 다리, 발, 배경 소품까지 마무리하면 릴레이가 끝나요.',
    bullets: ['이어지는 선을 놓치지 않게 힌트를 먼저 확인해요.', '마지막 프레임은 캐릭터의 분위기를 크게 바꿀 수 있어요.'],
    visual: {
      type: 'hint',
      image: LEGS_EXAMPLE,
      hintLabel: '힌트는 이렇게 연결부 중심으로 보여요',
      caption: '이전 사람이 그린 몸의 아래쪽 일부를 보고 다리 프레임을 이어가요.',
    },
  },
  {
    id: 'reveal',
    eyebrow: 'FINISH',
    title: '완성본을 공개해요',
    description:
      '얼굴, 몸, 다리 프레임을 한 번에 붙여서 모두가 만든 캐릭터를 확인합니다.',
    bullets: ['각 라운드의 그림이 세로로 합쳐져요.', '예상 밖의 연결이 릴레이 드로잉의 가장 큰 재미예요.'],
    visual: {
      type: 'stack',
      images: [FACE_EXAMPLE, BODY_EXAMPLE, LEGS_EXAMPLE],
      caption: '서로 다른 사람이 그린 세 조각이 하나의 결과물이 됩니다.',
    },
  },
]
