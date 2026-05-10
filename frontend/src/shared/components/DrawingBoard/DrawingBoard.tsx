'use client'

import { Circle, Group, Layer, Line, Rect, Stage } from 'react-konva'
import type { ReactNode } from 'react'
import type {
  DrawingArea,
  DrawingBoardSize,
  DrawingLine,
  DrawingPointerEvent,
} from '@/shared/types'
import RasterFillImage from './RasterFillImage'

interface DrawingBoardProps {
  boardSize: DrawingBoardSize
  lines: DrawingLine[]
  onionSkinLines?: DrawingLine[]
  drawArea?: DrawingArea
  backgroundColor?: string
  backgroundCornerRadius?: number
  gridColor?: string
  gridGap?: number
  onionSkinOpacity?: number
  className?: string
  childrenBeforeLines?: ReactNode
  childrenAfterLines?: ReactNode
  onDrawStart: (event: DrawingPointerEvent) => void
  onDrawMove: (event: DrawingPointerEvent) => void
  onDrawEnd: () => void
}

export default function DrawingBoard({
  boardSize,
  lines,
  onionSkinLines = [],
  drawArea,
  backgroundColor = '#fffdf7',
  backgroundCornerRadius = 16,
  gridColor = '#ffa8b8',
  gridGap = 20,
  onionSkinOpacity = 0.22,
  className,
  childrenBeforeLines,
  childrenAfterLines,
  onDrawStart,
  onDrawMove,
  onDrawEnd,
}: DrawingBoardProps) {
  const gridDots = []
  const clipArea = drawArea ?? { y: 0, height: boardSize.height }

  for (
    let horizontalPosition = 12;
    horizontalPosition < boardSize.width;
    horizontalPosition += gridGap
  ) {
    for (
      let verticalPosition = 12;
      verticalPosition < boardSize.height;
      verticalPosition += gridGap
    ) {
      gridDots.push({ x: horizontalPosition, y: verticalPosition })
    }
  }

  return (
    <Stage
      width={boardSize.width}
      height={boardSize.height}
      className={className}
      onMouseDown={onDrawStart}
      onMouseMove={onDrawMove}
      onMouseUp={onDrawEnd}
      onMouseLeave={onDrawEnd}
      onTouchStart={onDrawStart}
      onTouchMove={onDrawMove}
      onTouchEnd={onDrawEnd}
    >
      <Layer listening={false}>
        <Rect
          x={0}
          y={0}
          width={boardSize.width}
          height={boardSize.height}
          fill={backgroundColor}
          cornerRadius={backgroundCornerRadius}
        />

        {gridDots.map((dot) => (
          <Circle
            key={`${dot.x}-${dot.y}`}
            x={dot.x}
            y={dot.y}
            radius={1}
            fill={gridColor}
            opacity={0.72}
          />
        ))}

        {childrenBeforeLines}
      </Layer>

      {onionSkinLines.length > 0 && (
        <Layer listening={false}>
          <Group
            opacity={onionSkinOpacity}
            clipX={0}
            clipY={clipArea.y}
            clipWidth={boardSize.width}
            clipHeight={clipArea.height}
          >
            <DrawingLineGroup
              lines={onionSkinLines}
              boardSize={boardSize}
              eraserColor={backgroundColor}
            />
          </Group>
        </Layer>
      )}

      <Layer>
        <Group
          clipX={0}
          clipY={clipArea.y}
          clipWidth={boardSize.width}
          clipHeight={clipArea.height}
        >
          <DrawingLineGroup lines={lines} boardSize={boardSize} eraserColor={backgroundColor} />
        </Group>
      </Layer>

      <Layer listening={false}>
        {childrenAfterLines}
      </Layer>
    </Stage>
  )
}

function DrawingLineGroup({
  lines,
  boardSize,
  eraserColor,
}: {
  lines: DrawingLine[]
  boardSize: DrawingBoardSize
  eraserColor: string
}) {
  return (
    <>
      {lines.map((line) => {
        if (line.kind === 'fill') {
          if (line.imageDataUrl) {
            return (
              <RasterFillImage
                key={line.id}
                imageDataUrl={line.imageDataUrl}
                width={boardSize.width}
                height={boardSize.height}
              />
            )
          }

          return (
            <Line
              key={line.id}
              points={line.points.flatMap((point) => [point.x, point.y])}
              fill={line.color}
              closed
              listening={false}
            />
          )
        }

        return (
          <Line
            key={line.id}
            points={line.points.flatMap((point) => [point.x, point.y])}
            stroke={line.color}
            strokeWidth={line.strokeWidth}
            tension={0.45}
            lineCap="round"
            lineJoin="round"
            globalCompositeOperation={
              line.compositeOperation ??
              (line.color === eraserColor ? 'destination-out' : 'source-over')
            }
          />
        )
      })}
    </>
  )
}
