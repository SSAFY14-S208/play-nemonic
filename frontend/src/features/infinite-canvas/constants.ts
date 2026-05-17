export const INFINITE_CANVAS_COLOR_OPTIONS = [
  {
    assetSrc: '/images/infinite-canvas/palette-pink.png',
    id: 'pink',
    label: '분홍',
    value: '#F36DA8',
  },
  {
    assetSrc: '/images/infinite-canvas/palette-orange.png',
    id: 'orange',
    label: '주황',
    value: '#FFB14F',
  },
  {
    assetSrc: '/images/infinite-canvas/palette-yellow.png',
    id: 'yellow',
    label: '노랑',
    value: '#FFD84B',
  },
  {
    assetSrc: '/images/infinite-canvas/palette-green.png',
    id: 'green',
    label: '초록',
    value: '#65D88E',
  },
  {
    assetSrc: '/images/infinite-canvas/palette-blue.png',
    id: 'blue',
    label: '하늘',
    value: '#5DC7F2',
  },
  {
    assetSrc: '/images/infinite-canvas/palette-purple.png',
    id: 'purple',
    label: '보라',
    value: '#9D6DFF',
  },
  {
    assetSrc: '/images/infinite-canvas/palette-white.png',
    id: 'white',
    label: '흰색',
    value: '#FFFFFF',
  },
] as const

export type InfiniteCanvasColorOption = (typeof INFINITE_CANVAS_COLOR_OPTIONS)[number]
