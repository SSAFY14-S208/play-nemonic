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
    description: '생년월일을 넣으면 오늘의 운세가 작은 메모로 나와요.',
    detail:
      '생년월일을 입력하면 오늘의 운세가 작은 메모로 나와요. 완성된 메모는 보드에 붙여 친구들과 함께 볼 수 있어요.',
    id: 'fortune-memo',
    lightingColor: HUB_GAME_LIGHTING_COLORS['fortune-memo'],
    route: '/fortune',
    tagline: '오늘 기분을 가볍게 확인해요',
    title: '오늘의 운세 메모',
  },
  {
    accentColor: '#8fd8ff',
    description: '친구들과 한 장씩 이어 그려 짧은 GIF를 만들어요.',
    detail:
      '방을 만들고 친구를 초대해 한 프레임씩 이어 그려요. 완성되면 짧은 GIF처럼 재생되고, 결과를 보드에 남길 수 있어요.',
    id: 'flipbook',
    lightingColor: HUB_GAME_LIGHTING_COLORS.flipbook,
    route: '/flipbook',
    tagline: '한 프레임씩 이어 만드는 GIF',
    title: '플립북',
  },
  {
    accentColor: '#ff9fc2',
    description: '라운드마다 돌아가며 그림을 이어요.',
    detail:
      '친구들과 차례대로 그림을 이어 그려요. 마지막에 결과가 한 번에 열려서 예상 밖의 합작을 함께 볼 수 있어요.',
    id: 'relay-drawing',
    lightingColor: HUB_GAME_LIGHTING_COLORS['relay-drawing'],
    route: '/relay-drawing',
    tagline: '차례대로 완성하는 협동 드로잉',
    title: '릴레이 드로잉',
  },
  {
    accentColor: '#88dfff',
    description: '같은 캔버스에서 자유롭게 그리고 꾸며요.',
    detail:
      '초대 코드로 같은 캔버스에 모여 자유롭게 그려요. 펜, 도형, 텍스트, 스티커를 함께 쓰면서 큰 그림판처럼 놀 수 있어요.',
    id: 'infinite-canvas',
    lightingColor: HUB_GAME_LIGHTING_COLORS['infinite-canvas'],
    route: '/infinite-canvas',
    tagline: '함께 쓰는 큰 그림판',
    title: '무한 캔버스',
  },
]
