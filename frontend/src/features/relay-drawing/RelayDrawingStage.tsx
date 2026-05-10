'use client'

import { useEffect, useRef, useState } from 'react'
import { Group, Layer, Line, Rect, Stage, Text } from 'react-konva'
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

  // 라운드별 canvas 높이가 다르다. body/legs는 상단에 incoming hint zone이 있어
  // 720+120=840, face는 720. aspect ratio도 라운드에 따라 달라진다.
  const activeRoundRule = RELAY_ROUND_RULES[activeRoundKey]
  const roundCanvasHeight = activeRoundRule.canvasHeight
  const stageAspectRatio = RELAY_STAGE_SIZE.width / roundCanvasHeight

  const containerRef = useRef<HTMLDivElement>(null)
  const [stageDimensions, setStageDimensions] = useState({
    width: RELAY_STAGE_SIZE.width,
    height: roundCanvasHeight,
    scale: 1,
  })

  // 컨테이너 크기에 맞춰 Stage 사이즈를 비례 조정. ResizeObserver 콜백은 effect
  // 본문 동기 setState가 아니라 별도 callback으로 fire되므로 React Compiler 룰을
  // 위반하지 않는다. raf로 첫 측정도 똑같이 비동기화.
  // 라운드 전환 시(canvasHeight 변경) effect가 재실행돼서 새 비율로 재측정한다.
  useEffect(() => {
    const containerElement = containerRef.current
    if (!containerElement) return

    const updateStageDimensions = () => {
      const rect = containerElement.getBoundingClientRect()
      const containerWidth = rect.width
      const containerHeight = rect.height
      if (containerWidth === 0 || containerHeight === 0) return
      // 가로/세로 중 작은 비율로 uniform scale — aspect-ratio를 보존한다.
      const widthRatio = containerWidth / RELAY_STAGE_SIZE.width
      const heightRatio = containerHeight / roundCanvasHeight
      const scale = Math.min(widthRatio, heightRatio, 1)
      setStageDimensions({
        width: RELAY_STAGE_SIZE.width * scale,
        height: roundCanvasHeight * scale,
        scale,
      })
    }

    const raf = requestAnimationFrame(updateStageDimensions)
    const observer = new ResizeObserver(updateStageDimensions)
    observer.observe(containerElement)
    return () => {
      cancelAnimationFrame(raf)
      observer.disconnect()
    }
  }, [roundCanvasHeight])

  const lines = roundLines[activeRoundKey]
  const activeRoundIndex = RELAY_ROUND_ORDER.findIndex(
    (roundKey) => roundKey === activeRoundKey,
  )
  const previousRoundKey = activeRoundIndex > 0 ? RELAY_ROUND_ORDER[activeRoundIndex - 1] : null
  const previousRoundLines = previousRoundKey ? roundLines[previousRoundKey] : []

  // BODY/LEGS 라운드에서는 힌트 콘텐츠 유무와 관계없이 힌트 영역을 표시한다.
  // 이전 사람이 아무것도 그리지 않아 서버가 빈 제출을 처리한 경우에도
  // 가이드 라인과 안내 텍스트가 보여야 사용자가 그릴 위치를 파악할 수 있다.
  const shouldShowPreviousHint =
    activeRoundRule.incomingHintSourceArea !== undefined &&
    activeRoundRule.incomingHintTargetArea !== undefined
  const incomingHintSourceArea = activeRoundRule.incomingHintSourceArea
  const incomingHintTargetArea = activeRoundRule.incomingHintTargetArea
  // hint zone 안에서 이전 사람의 outgoing 영역이 그대로 라인 좌표를 갖고 있어서
  // 그 좌표 그대로 hint target에 보여주려면 source.y → target.y 변환이 필요하다.
  const hintVerticalOffset =
    incomingHintSourceArea && incomingHintTargetArea
      ? incomingHintTargetArea.y - incomingHintSourceArea.y
      : 0
  const helperTextVerticalPosition = incomingHintTargetArea
    ? incomingHintTargetArea.y + incomingHintTargetArea.height + 18
    : 24

  return (
    <div
      ref={containerRef}
      className="grid h-full w-full place-items-center"
      style={{ aspectRatio: stageAspectRatio }}
    >
      <Stage
        width={stageDimensions.width}
        height={stageDimensions.height}
        scaleX={stageDimensions.scale}
        scaleY={stageDimensions.scale}
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
            height={roundCanvasHeight}
            fill="#fffdf7"
            cornerRadius={16}
          />

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
    </div>
  )
}
