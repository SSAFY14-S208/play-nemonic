'use client'

import { Circle, Group, Layer, Line, Rect, Stage, Text } from 'react-konva'
import type { KonvaEventObject } from 'konva/lib/Node'
import {
  RELAY_ROUND_RULES,
  RELAY_STAGE_SIZE,
  type RelayRoundKey,
} from './constants'
import { OutgoingHint, PreviousRoundHint } from './components/drawing-stage'
import type { RelayDrawLine } from './useRelayDrawing'

interface RelayDrawingStageProps {
  activeRoundKey: RelayRoundKey
  lines: RelayDrawLine[]
  previousRoundLines: RelayDrawLine[]
  onDrawStart: (event: KonvaEventObject<MouseEvent | TouchEvent>) => void
  onDrawMove: (event: KonvaEventObject<MouseEvent | TouchEvent>) => void
  onDrawEnd: () => void
}

export default function RelayDrawingStage({
  activeRoundKey,
  lines,
  previousRoundLines,
  onDrawStart,
  onDrawMove,
  onDrawEnd,
}: RelayDrawingStageProps) {
  const gridDots = []
  const activeRoundRule = RELAY_ROUND_RULES[activeRoundKey]
  const fillLines = lines.filter((line) => line.kind === 'fill')
  const strokeLines = lines.filter((line) => line.kind !== 'fill')
  const shouldShowPreviousHint =
    previousRoundLines.length > 0 &&
    activeRoundRule.incomingHintSourceArea !== undefined &&
    activeRoundRule.incomingHintTargetArea !== undefined
  const incomingHintSourceArea = activeRoundRule.incomingHintSourceArea
  const incomingHintTargetArea = activeRoundRule.incomingHintTargetArea
  const hintVerticalOffset =
    incomingHintSourceArea && incomingHintTargetArea
      ? incomingHintTargetArea.y - incomingHintSourceArea.y
      : 0
  const helperTextVerticalPosition = incomingHintTargetArea
    ? incomingHintTargetArea.y + incomingHintTargetArea.height + 18
    : 24

  for (
    let horizontalPosition = 12;
    horizontalPosition < RELAY_STAGE_SIZE.width;
    horizontalPosition += 20
  ) {
    for (
      let verticalPosition = 12;
      verticalPosition < RELAY_STAGE_SIZE.height;
      verticalPosition += 20
    ) {
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

        <Rect
          x={0}
          y={activeRoundRule.drawArea.y}
          width={RELAY_STAGE_SIZE.width}
          height={activeRoundRule.drawArea.height}
          fill="transparent"
          stroke="#ef9f91"
          strokeWidth={1.5}
          opacity={0.72}
        />

        {shouldShowPreviousHint && incomingHintTargetArea && (
          <PreviousRoundHint
            hintTargetArea={incomingHintTargetArea}
            hintVerticalOffset={hintVerticalOffset}
            previousRoundLines={previousRoundLines}
          />
        )}

        {activeRoundRule.outgoingHintArea && (
          <OutgoingHint outgoingHintArea={activeRoundRule.outgoingHintArea} />
        )}

        <Text
          x={16}
          y={helperTextVerticalPosition}
          text={activeRoundRule.helperText}
          fontFamily="Pretendard Variable"
          fontSize={14}
          fontStyle="bold"
          fill="#d49b1f"
        />

        <Group
          clipX={0}
          clipY={activeRoundRule.drawArea.y}
          clipWidth={RELAY_STAGE_SIZE.width}
          clipHeight={activeRoundRule.drawArea.height}
        >
          {fillLines.map((line) => (
            <Line
              key={line.id}
              points={line.points.flatMap((point) => [point.x, point.y])}
              fill={line.color}
              closed
              opacity={0.56}
              listening={false}
            />
          ))}

          {strokeLines.map((line) => (
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
        </Group>
      </Layer>
    </Stage>
  )
}
