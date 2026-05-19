export type InfinityPhase = 'lobby' | 'stage' | 'result'

export type InfinityToolKey =
  | 'pen'
  | 'eraser'
  | 'bucket'
  | 'select'
  | 'hand'
  | 'select-eraser'
  | 'shape-rect'
  | 'shape-ellipse'
  | 'shape-rect-fill'
  | 'shape-ellipse-fill'
  | 'text'

export const INFINITY_COLORS = [
  '#f4a7b9', // pink
  '#f9c89b', // orange
  '#f9e887', // yellow
  '#9dd6b0', // green
  '#9bbde8', // blue (default)
  '#c9b3e8', // purple
] as const

export const INFINITY_PARTICIPANT_ACCENTS = [
  '#2d3a55',
  '#ffffff',
  '#ffbf3f',
  '#2bb3a3',
  '#ef5b7d',
  '#7b61ff',
  '#1f8fd1',
  '#43b77a',
  '#f28c28',
  '#7c5c8a',
] as const

export const INFINITY_ANIMALS = [
  { key: 'fox', emoji: '🦊', name: '여우' },
  { key: 'cat', emoji: '🐱', name: '고양이' },
  { key: 'bear', emoji: '🐻', name: '곰돌이' },
  { key: 'rabbit', emoji: '🐰', name: '토끼' },
  { key: 'dinosaur', emoji: '🦕', name: '공룡이' },
  { key: 'penguin', emoji: '🐧', name: '펭귄이' },
] as const

export type InfinityAnimal = (typeof INFINITY_ANIMALS)[number]

export const INFINITY_STROKE_WIDTHS = [2, 5, 10] as const
export const INFINITY_LINE_TENSION = 0

export interface InfinityLine {
  id: string
  type: 'line'
  color: string
  strokeWidth: number
  points: { x: number; y: number }[]
  isEraser?: boolean
}

export interface InfinityFill {
  id: string
  type: 'fill'
  x: number
  y: number
  width: number
  height: number
  color: string
  imageDataUrl: string
}

export interface InfinityShape {
  id: string
  type: 'rect' | 'ellipse'
  /** top-left x */
  x: number
  /** top-left y */
  y: number
  width: number
  height: number
  color: string
  strokeWidth: number
  rotation?: number
  /** when set, shape is rendered as filled solid (no stroke) */
  fill?: string
}

export interface InfinityText {
  id: string
  type: 'text'
  x: number
  y: number
  text: string
  fontSize: number
  color: string
  fontFamily?: string
  rotation?: number
}

export interface InfinityImage {
  id: string
  type: 'image'
  x: number
  y: number
  width: number
  height: number
  src: string
  objectKey?: string
  naturalWidth?: number
  naturalHeight?: number
  rotation?: number
  metadata?: Record<string, unknown>
}

export const INFINITY_TEXT_FONT_SIZES = [12, 16, 20, 24, 32, 48, 64] as const
export const INFINITY_TEXT_DEFAULT_FONT_SIZE = 24
export const INFINITY_TEXT_DEFAULT_COLOR = '#111111'
export const INFINITY_TEXT_DEFAULT_FONT_FAMILY = "'Pretendard Variable', Pretendard, sans-serif"

export const INFINITY_TEXT_FONT_FAMILIES = [
  {
    label: '기본',
    value: INFINITY_TEXT_DEFAULT_FONT_FAMILY,
  },
  {
    label: '고딕',
    value: "'Noto Sans KR', 'Malgun Gothic', sans-serif",
  },
  {
    label: '명조',
    value: "'Nanum Myeongjo', 'Batang', serif",
  },
  {
    label: '영문',
    value: 'Georgia, serif',
  },
  {
    label: '코드',
    value: "'Courier New', monospace",
  },
] as const

export type InfinityObject = InfinityLine | InfinityFill | InfinityShape | InfinityText | InfinityImage
