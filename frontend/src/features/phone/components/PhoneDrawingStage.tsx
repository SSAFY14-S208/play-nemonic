'use client'

import type Konva from 'konva'
import type { KonvaEventObject } from 'konva/lib/Node'
import type { RefObject } from 'react'
import { Layer, Line, Rect, Stage } from 'react-konva'
import {
  PHONE_DRAWING_PAPER_COLOR,
  PHONE_DRAWING_STAGE_SIZE,
} from '../constants'
import type { PhoneDrawLine } from '../types'

interface PhoneDrawingStageProps {
  lines: PhoneDrawLine[]
  onDrawEnd: () => void
  onDrawMove: (event: KonvaEventObject<MouseEvent | TouchEvent>) => void
  onDrawStart: (event: KonvaEventObject<MouseEvent | TouchEvent>) => void
  stageRef: RefObject<Konva.Stage | null>
}

export function PhoneDrawingStage({
  lines,
  onDrawEnd,
  onDrawMove,
  onDrawStart,
  stageRef,
}: PhoneDrawingStageProps) {
  return (
    <Stage
      ref={stageRef}
      width={PHONE_DRAWING_STAGE_SIZE.width}
      height={PHONE_DRAWING_STAGE_SIZE.height}
      className="h-full w-full"
      onMouseDown={onDrawStart}
      onMouseMove={onDrawMove}
      onMouseUp={onDrawEnd}
      onMouseLeave={onDrawEnd}
      onTouchStart={onDrawStart}
      onTouchMove={onDrawMove}
      onTouchEnd={onDrawEnd}
    >
      <Layer>
        <Rect
          x={0}
          y={0}
          width={PHONE_DRAWING_STAGE_SIZE.width}
          height={PHONE_DRAWING_STAGE_SIZE.height}
          fill={PHONE_DRAWING_PAPER_COLOR}
        />
        {lines.map((line) => (
          <Line
            key={line.id}
            points={line.points}
            stroke={line.color}
            strokeWidth={line.strokeWidth}
            tension={0.45}
            lineCap="round"
            lineJoin="round"
          />
        ))}
      </Layer>
    </Stage>
  )
}
