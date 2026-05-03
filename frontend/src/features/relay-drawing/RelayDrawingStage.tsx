'use client'

import { Circle, Group, Layer, Line, Rect, Stage, Text } from 'react-konva'
import type { KonvaEventObject } from 'konva/lib/Node'
import {
  RELAY_ROUND_RULES,
  RELAY_STAGE_SIZE,
  type RelayRoundKey,
} from './constants'
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
  const shouldShowPreviousHint =
    previousRoundLines.length > 0 &&
    Boolean(activeRoundRule.incomingHintSourceArea) &&
    Boolean(activeRoundRule.incomingHintTargetArea)
  const hintVerticalOffset =
    activeRoundRule.incomingHintSourceArea && activeRoundRule.incomingHintTargetArea
      ? activeRoundRule.incomingHintTargetArea.y - activeRoundRule.incomingHintSourceArea.y
      : 0

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
          fill="#fff9ea"
          opacity={0.55}
          stroke="#ff2a24"
          strokeWidth={3}
        />

        {shouldShowPreviousHint && (
          <Group>
            <Rect
              x={0}
              y={activeRoundRule.incomingHintTargetArea?.y ?? 0}
              width={RELAY_STAGE_SIZE.width}
              height={activeRoundRule.incomingHintTargetArea?.height ?? 0}
              fill="#6dd5f4"
              opacity={0.74}
              stroke="#119ec8"
              strokeWidth={3}
            />
            <Text
              x={46}
              y={(activeRoundRule.incomingHintTargetArea?.y ?? 0) + 12}
              text="이 선을 이어가세요"
              fontFamily="Pretendard Variable"
              fontSize={14}
              fontStyle="bold"
              fill="#947c40"
            />
            <Group
              clipX={0}
              clipY={activeRoundRule.incomingHintTargetArea?.y ?? 0}
              clipWidth={RELAY_STAGE_SIZE.width}
              clipHeight={activeRoundRule.incomingHintTargetArea?.height ?? 0}
            >
              {previousRoundLines.map((line) => (
                <Line
                  key={`preview-${line.id}`}
                  points={line.points.flatMap((point) => [
                    point.x,
                    point.y + hintVerticalOffset,
                  ])}
                  stroke={line.color}
                  strokeWidth={line.strokeWidth}
                  tension={0.45}
                  lineCap="round"
                  lineJoin="round"
                  opacity={0.62}
                />
              ))}
            </Group>
            <DashedGuide
              verticalPosition={
                (activeRoundRule.incomingHintTargetArea?.y ?? 0) +
                (activeRoundRule.incomingHintTargetArea?.height ?? 0)
              }
            />
          </Group>
        )}

        {activeRoundRule.outgoingHintArea && (
          <Group>
            <Rect
              x={0}
              y={activeRoundRule.outgoingHintArea.y}
              width={RELAY_STAGE_SIZE.width}
              height={activeRoundRule.outgoingHintArea.height}
              fill="#6dd5f4"
              opacity={0.74}
              stroke="#119ec8"
              strokeWidth={3}
            />
            <Text
              x={16}
              y={activeRoundRule.outgoingHintArea.y + activeRoundRule.outgoingHintArea.height - 28}
              text="이 구간만 다음 사람에게 보여요"
              fontFamily="Pretendard Variable"
              fontSize={13}
              fontStyle="bold"
              fill="#947c40"
            />
          </Group>
        )}

        <Text
          x={16}
          y={activeRoundRule.drawArea.y + 12}
          text={activeRoundRule.helperText}
          fontFamily="Pretendard Variable"
          fontSize={14}
          fontStyle="bold"
          fill="#d49b1f"
        />
        <DashedGuide verticalPosition={activeRoundRule.drawArea.y} />
        <DashedGuide
          verticalPosition={activeRoundRule.drawArea.y + activeRoundRule.drawArea.height}
        />

        <Group
          clipX={0}
          clipY={activeRoundRule.drawArea.y}
          clipWidth={RELAY_STAGE_SIZE.width}
          clipHeight={activeRoundRule.drawArea.height}
        >
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
        </Group>
      </Layer>
    </Stage>
  )
}

function DashedGuide({ verticalPosition }: { verticalPosition: number }) {
  const dashSegments = []

  for (
    let horizontalPosition = 8;
    horizontalPosition < RELAY_STAGE_SIZE.width;
    horizontalPosition += 12
  ) {
    dashSegments.push(horizontalPosition)
  }

  return (
    <>
      {dashSegments.map((horizontalPosition) => (
        <Rect
          key={`${horizontalPosition}-${verticalPosition}`}
          x={horizontalPosition}
          y={verticalPosition}
          width={6}
          height={2}
          fill="#d49b1f"
          opacity={0.38}
          cornerRadius={1}
        />
      ))}
    </>
  )
}
