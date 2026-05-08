'use client'

import { Circle, Group, Layer, Line, Rect, Stage, Text } from 'react-konva'
import {
  RELAY_ROUND_ORDER,
  RELAY_ROUND_RULES,
  RELAY_STAGE_SIZE,
} from './constants'
import {
  OutgoingHint,
  PreviousRoundHint,
  RasterFillImage,
} from './components/drawing-stage'
import { useRelayDrawingStore } from './stores'
import { useRelayCanvas } from './hooks'

export default function RelayDrawingStage() {
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey)
  const roundLines = useRelayDrawingStore((state) => state.roundLines)
  const hintImageUrl = useRelayDrawingStore((state) => state.hintImageUrl)

  const { beginDrawing, continueDrawing, endDrawing } = useRelayCanvas()

  const lines = roundLines[activeRoundKey]
  const activeRoundIndex = RELAY_ROUND_ORDER.findIndex(
    (roundKey) => roundKey === activeRoundKey,
  )
  const previousRoundKey = activeRoundIndex > 0 ? RELAY_ROUND_ORDER[activeRoundIndex - 1] : null
  const previousRoundLines = previousRoundKey ? roundLines[previousRoundKey] : []

  const gridDots = []
  const activeRoundRule = RELAY_ROUND_RULES[activeRoundKey]
  // 서버 힌트 이미지가 있거나 로컬 라인이 있으면 이전 라운드 힌트를 표시한다.
  const hasHintContent = hintImageUrl !== null || previousRoundLines.length > 0
  const shouldShowPreviousHint =
    hasHintContent &&
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
      onMouseDown={beginDrawing}
      onMouseMove={continueDrawing}
      onMouseUp={endDrawing}
      onMouseLeave={endDrawing}
      onTouchStart={beginDrawing}
      onTouchMove={continueDrawing}
      onTouchEnd={endDrawing}
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
            hintImageUrl={hintImageUrl}
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
          {lines.map((line) => {
            if (line.kind === 'fill') {
              if (line.imageDataUrl) {
                return <RasterFillImage key={line.id} imageDataUrl={line.imageDataUrl} />
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
                globalCompositeOperation={line.color === '#fffdf7' ? 'destination-out' : 'source-over'}
              />
            )
          })}
        </Group>
      </Layer>
    </Stage>
  )
}
