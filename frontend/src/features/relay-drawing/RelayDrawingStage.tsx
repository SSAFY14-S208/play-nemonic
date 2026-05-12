'use client'

import { useEffect, useRef, useState } from 'react'
import { Group, Layer, Line, Rect, Stage, Text } from 'react-konva'
import {
  OVERLAP_HEIGHT,
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

  const activeRoundRule = RELAY_ROUND_RULES[activeRoundKey]
  const stageAspectRatio = RELAY_STAGE_SIZE.width / RELAY_STAGE_SIZE.height

  const containerRef = useRef<HTMLDivElement>(null)
  const [stageDimensions, setStageDimensions] = useState({
    width: RELAY_STAGE_SIZE.width,
    height: RELAY_STAGE_SIZE.height,
    scale: 1,
  })

  // 컨테이너 크기에 맞춰 Stage 사이즈를 비례 조정. ResizeObserver 콜백은 effect
  // 본문 동기 setState가 아니라 별도 callback으로 fire되므로 React Compiler 룰을
  // 위반하지 않는다. raf로 첫 측정도 똑같이 비동기화.
  useEffect(() => {
    const containerElement = containerRef.current
    if (!containerElement) return

    const updateStageDimensions = () => {
      const rect = containerElement.getBoundingClientRect()
      const containerWidth = rect.width
      const containerHeight = rect.height
      if (containerWidth === 0 || containerHeight === 0) return
      const widthRatio = containerWidth / RELAY_STAGE_SIZE.width
      const heightRatio = containerHeight / RELAY_STAGE_SIZE.height
      const scale = Math.min(widthRatio, heightRatio, 1)
      setStageDimensions({
        width: RELAY_STAGE_SIZE.width * scale,
        height: RELAY_STAGE_SIZE.height * scale,
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
  }, [])

  const lines = roundLines[activeRoundKey]

  // BODY/LEGS에서는 이전 파트의 힌트 이미지를 drawArea 상단에 오버레이로 표시.
  const shouldShowHintOverlay = activeRoundKey !== 'face'
  const helperTextVerticalPosition = shouldShowHintOverlay
    ? OVERLAP_HEIGHT + 18
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
            height={RELAY_STAGE_SIZE.height}
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

          {shouldShowHintOverlay && (
            <PreviousRoundHint
              overlayHeight={OVERLAP_HEIGHT}
              hintImageUrl={hintImageUrl}
            />
          )}

          {activeRoundRule.outgoingHintArea && (
            <OutgoingHint outgoingHintArea={activeRoundRule.outgoingHintArea} />
          )}

          <Text
            x={16}
            y={helperTextVerticalPosition}
            text={activeRoundRule.helperText}
            fontFamily="Paperlogy"
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
