'use client'

import { Circle, Ellipse, Group, Layer, Line, Rect, Stage, Text } from 'react-konva'
import type { KonvaEventObject } from 'konva/lib/Node'
import { RELAY_PREVIEW_LINES, RELAY_STAGE_SIZE } from './constants'
import type { RelayDrawLine } from './useRelayDrawing'

interface RelayDrawingStageProps {
  lines: RelayDrawLine[]
  onDrawStart: (event: KonvaEventObject<MouseEvent | TouchEvent>) => void
  onDrawMove: (event: KonvaEventObject<MouseEvent | TouchEvent>) => void
  onDrawEnd: () => void
}

export default function RelayDrawingStage({
  lines,
  onDrawStart,
  onDrawMove,
  onDrawEnd,
}: RelayDrawingStageProps) {
  const gridDots = []

  for (let horizontalPosition = 12; horizontalPosition < RELAY_STAGE_SIZE.width; horizontalPosition += 20) {
    for (let verticalPosition = 120; verticalPosition < RELAY_STAGE_SIZE.height - 12; verticalPosition += 20) {
      gridDots.push({ x: horizontalPosition, y: verticalPosition })
    }
  }

  return (
    <Stage
      width={RELAY_STAGE_SIZE.width}
      height={RELAY_STAGE_SIZE.height}
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
          width={RELAY_STAGE_SIZE.width}
          height={RELAY_STAGE_SIZE.height}
          fill="#fffdf7"
          cornerRadius={16}
        />

        {gridDots.map((dot, index) => (
          <Circle
            key={`${dot.x}-${dot.y}-${index}`}
            x={dot.x}
            y={dot.y}
            radius={1}
            fill="#ffdc82"
            opacity={0.72}
          />
        ))}

        <Group>
          <Rect
            x={0}
            y={0}
            width={RELAY_STAGE_SIZE.width}
            height={100}
            fill="#fff1c8"
            cornerRadius={[16, 16, 0, 0]}
          />
          <Text
            x={46}
            y={26}
            text="이전 사람의 그림 (하단 일부)"
            fontFamily="Pretendard Variable"
            fontSize={14}
            fontStyle="bold"
            fill="#947c40"
          />
          <Text
            x={RELAY_STAGE_SIZE.width / 2 - 96}
            y={112}
            text="↓ 여기부터 이어 그리세요"
            fontFamily="Pretendard Variable"
            fontSize={14}
            fontStyle="bold"
            fill="#ffd56f"
          />
          <Circle
            x={RELAY_PREVIEW_LINES.faceCenterX}
            y={-24}
            radius={110}
            fill="#f8d5b4"
            stroke="#2f2a1e"
            strokeWidth={3}
          />
          <Ellipse
            x={RELAY_PREVIEW_LINES.faceCenterX}
            y={36}
            radiusX={30}
            radiusY={9}
            fill="#f8d5b4"
            stroke="#2f2a1e"
            strokeWidth={3}
          />
        </Group>

        <Line
          points={[320, RELAY_PREVIEW_LINES.bodyTopY, 388, RELAY_PREVIEW_LINES.bodyTopY + 12]}
          stroke="#2f2a1e"
          strokeWidth={4}
          lineCap="round"
        />
        <Line
          points={[530, RELAY_PREVIEW_LINES.bodyTopY, 462, RELAY_PREVIEW_LINES.bodyTopY + 12]}
          stroke="#2f2a1e"
          strokeWidth={4}
          lineCap="round"
        />
        <Line
          points={[328, 242, 328, 390]}
          stroke="#ff5f67"
          strokeWidth={4}
          lineCap="round"
        />
        <Line
          points={[520, 242, 520, 390]}
          stroke="#ff5f67"
          strokeWidth={4}
          lineCap="round"
        />
        {[264, 306, 348, 390].map((verticalPosition) => (
          <Circle
            key={verticalPosition}
            x={RELAY_PREVIEW_LINES.faceCenterX}
            y={verticalPosition}
            radius={7}
            fill="#ff5f67"
          />
        ))}

        {lines.map((line) => (
          <Line
            key={line.id}
            points={line.points.flatMap((point) => [point.x, point.y])}
            stroke={line.color}
            strokeWidth={line.strokeWidth}
            tension={0.45}
            lineCap="round"
            lineJoin="round"
            globalCompositeOperation={line.color === '#fffdf7' ? 'destination-out' : 'source-over'}
          />
        ))}
      </Layer>
    </Stage>
  )
}
