export const INFINITE_CANVAS_COLOR_OPTIONS = [
  { id: 'pink', label: '분홍', value: '#F36DA8' },
  { id: 'orange', label: '주황', value: '#FFB14F' },
  { id: 'yellow', label: '노랑', value: '#FFD84B' },
  { id: 'green', label: '초록', value: '#65D88E' },
  { id: 'sky', label: '하늘', value: '#5DC7F2' },
  { id: 'violet', label: '보라', value: '#9D6DFF' },
  { id: 'white', label: '흰색', value: '#FFFFFF' },
] as const

export type InfiniteCanvasColorOption = (typeof INFINITE_CANVAS_COLOR_OPTIONS)[number]
