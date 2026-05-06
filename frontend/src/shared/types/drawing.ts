import type { KonvaEventObject } from 'konva/lib/Node'

export interface DrawingPoint {
  x: number
  y: number
}

export interface DrawingArea {
  y: number
  height: number
}

export interface DrawingBoardSize {
  width: number
  height: number
}

export type DrawingToolKey = 'pencil' | 'marker' | 'bucket' | 'eraser'

export interface DrawingLine {
  id: string
  color: string
  strokeWidth: number
  points: DrawingPoint[]
  kind?: 'stroke' | 'fill'
  compositeOperation?: 'source-over' | 'destination-out'
  imageDataUrl?: string
}

export type DrawingPointerEvent = KonvaEventObject<MouseEvent | TouchEvent>
