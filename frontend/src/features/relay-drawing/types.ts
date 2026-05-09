import type { RelayRoundKey } from './constants'

export interface RelayDrawPoint {
  x: number
  y: number
}

export interface RelayDrawLine {
  id: string
  color: string
  strokeWidth: number
  points: RelayDrawPoint[]
  kind?: 'stroke' | 'fill'
  imageDataUrl?: string
}

export type RelayRoundLines = Record<RelayRoundKey, RelayDrawLine[]>

export interface RelayCompositeDrawingPayload {
  rounds: RelayRoundLines
  mergedLines: RelayDrawLine[]
  completedAt: string | null
}
