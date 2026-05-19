import type { HubGame, HubGameLightingId } from '@/shared/types'

export const HUB_GAME_LIGHTING_COLORS: Record<HubGameLightingId, string> = {
  'community-canvas': '#96d27f',
  'relay-drawing': '#ffd56f',
  flipbook: '#f58c97',
  'fortune-memo': '#a281d0',
  'infinite-canvas': '#8fd8ff',
}

export const HUB_GAMES: HubGame[] = [
  {
    accentColor: '#c7a6ff',
    description: '오늘의 기분과 운세를 작은 메모처럼 뽑아보세요.',
    id: 'fortune-memo',
    lightingColor: HUB_GAME_LIGHTING_COLORS['fortune-memo'],
    route: '/fortune',
    title: '오늘의 운세 메모',
  },
  {
    accentColor: '#8fd8ff',
    description: '짧은 장면을 넘겨보는 감성 애니메이션 노트.',
    id: 'flipbook',
    lightingColor: HUB_GAME_LIGHTING_COLORS.flipbook,
    route: '/flipbook',
    title: '플립북',
  },
  {
    accentColor: '#ff9fc2',
    description: '여러 사람의 선을 이어 새로운 그림을 완성해요.',
    id: 'relay-drawing',
    lightingColor: HUB_GAME_LIGHTING_COLORS['relay-drawing'],
    route: '/relay-drawing',
    title: '릴레이 드로잉',
  },
  {
    accentColor: '#88dfff',
    description: '끝없이 펼쳐지는 캔버스 위에 상상을 남겨보세요.',
    id: 'infinite-canvas',
    lightingColor: HUB_GAME_LIGHTING_COLORS['infinite-canvas'],
    route: '/infinite-canvas',
    title: '무한 캔버스',
  },
]
