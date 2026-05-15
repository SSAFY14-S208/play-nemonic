import type { HubGame } from '@/shared/types'

export const HUB_GAMES: HubGame[] = [
  {
    accentColor: '#c7a6ff',
    description: '오늘의 기분과 운세를 작은 메모처럼 뽑아보세요.',
    id: 'fortune-memo',
    route: '/fortune',
    title: '오늘의 운세 메모',
  },
  {
    accentColor: '#8fd8ff',
    description: '짧은 장면을 넘겨보는 감성 애니메이션 노트.',
    id: 'flipbook',
    route: '/flipbook',
    title: '플립북',
  },
  {
    accentColor: '#ff9fc2',
    description: '여러 사람의 선을 이어 새로운 그림을 완성해요.',
    id: 'relay-drawing',
    route: '/relay-drawing',
    title: '릴레이 드로잉',
  },
]
