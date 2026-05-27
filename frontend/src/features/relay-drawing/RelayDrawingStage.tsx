'use client'

import { useEffect, useRef, useState } from 'react'
import { Layer, Rect, Stage, Text } from 'react-konva'
import { OVERLAP_HEIGHT, RELAY_ROUND_RULES, RELAY_STAGE_SIZE } from './constants'
import { DrawingLinesLayer, OutgoingHint, PreviousRoundHint } from './views/RelayDrawingView/sections/DrawingStage'
import { useRelayDrawingStore } from './stores'
import { useRelayCanvas } from './hooks'
import { isPointInsideArea } from './utils/canvas-rendering'

export default function RelayDrawingStage() {
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey)
  const roundLines = useRelayDrawingStore((state) => state.roundLines)
  const hintImageUrl = useRelayDrawingStore((state) => state.hintImageUrl)
  const isSubmitting = useRelayDrawingStore((state) => state.isSubmitting)
  const isSubmitted = useRelayDrawingStore((state) => state.isSubmitted)
  const isPartTimeUp = useRelayDrawingStore((state) => state.isPartTimeUp)

  const { beginDrawing, continueDrawing, endDrawing } = useRelayCanvas()

  const activeRoundRule = RELAY_ROUND_RULES[activeRoundKey]
  const stageAspectRatio = RELAY_STAGE_SIZE.width / RELAY_STAGE_SIZE.height

  const containerRef = useRef<HTMLDivElement>(null)
  const [stageDimensions, setStageDimensions] = useState<{
    width: number
    height: number
    scale: number
  } | null>(null)

  // 컨테이너 크기에 맞춰 Stage 사이즈를 비례 조정. ResizeObserver 콜백은 effect
  // 본문 동기 setState가 아니라 별도 callback으로 fire되므로 React Compiler 룰을
  // 위반하지 않는다. raf로 첫 측정도 똑같이 비동기화.
  //
  // 데스크탑 RelayDrawingView가 부모 div에 CSS transform: scale(...) 을 적용해
  // 1536×1024 디자인 전체를 viewport에 맞춰 줄인다. getBoundingClientRect()는
  // 그 transform이 반영된 viewport 좌표라 Konva Stage가 이미 줄어든 사이즈를
  // 또 한 번 줄여 결과적으로 이중 스케일이 된다. offsetWidth/offsetHeight는
  // layout 좌표(transform 무시)를 반환하므로, 데스크탑처럼 부모가 transform 되어
  // 있어도 Stage는 디자인 좌표 그대로 측정되어 한 번만 스케일된다.
  useEffect(() => {
    const containerElement = containerRef.current
    if (!containerElement) return

    const updateStageDimensions = () => {
      const containerWidth = containerElement.offsetWidth
      const containerHeight = containerElement.offsetHeight
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

  // outgoing 힌트 영역 안내 — 사용자가 힌트 영역 안에 한 번이라도 점을 찍었는지
  // 검사한다. 캔버스의 다른 위치(얼굴 영역 등)에 그리는 건 안내 표시에 영향이 없다.
  // 라운드가 바뀌면 setAssignment가 roundLines/isSubmitting/isSubmitted/isPartTimeUp
  // 을 모두 리셋하므로 자동으로 다시 true가 된다.
  // 양동이 raster fill은 캔버스 전체를 덮을 수 있어 hint 영역에 색이 들어간 것으로
  // 간주한다. polygon fill은 points 검사로 정확히 판정된다.
  const outgoingHintArea = activeRoundRule.outgoingHintArea
  const hasDrawnInOutgoingHintArea = outgoingHintArea
    ? lines.some((line) => {
        if (line.kind === 'fill' && line.imageDataUrl) return true
        return line.points.some((point) =>
          isPointInsideArea(point, outgoingHintArea),
        )
      })
    : false
  const isOutgoingHintAttentionVisible =
    outgoingHintArea !== undefined &&
    !hasDrawnInOutgoingHintArea &&
    !isSubmitting &&
    !isSubmitted &&
    !isPartTimeUp

  // BODY/LEGS에서는 이전 파트의 힌트 이미지를 drawArea 상단에 오버레이로 표시.
  const shouldShowHintOverlay = activeRoundKey !== 'face'
  const helperTextVerticalPosition = shouldShowHintOverlay
    ? OVERLAP_HEIGHT + 18
    : 24

  return (
    <div
      ref={containerRef}
      className="grid h-full w-full place-items-center touch-none"
      style={{ aspectRatio: stageAspectRatio }}
    >
      {stageDimensions !== null && (
        <Stage
          width={stageDimensions.width}
          height={stageDimensions.height}
          scaleX={stageDimensions.scale}
          scaleY={stageDimensions.scale}
          style={{ touchAction: 'none' }}
          onMouseDown={beginDrawing}
          onMouseMove={continueDrawing}
          onMouseUp={endDrawing}
          onMouseLeave={endDrawing}
          onTouchStart={beginDrawing}
          onTouchMove={continueDrawing}
          onTouchEnd={endDrawing}
        >
          {/* 배경 레이어 — destination-out 지우개가 이 레이어를 침범하지 않도록 분리. */}
          <Layer listening={false}>
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
              <OutgoingHint
                outgoingHintArea={activeRoundRule.outgoingHintArea}
                isAttentionVisible={isOutgoingHintAttentionVisible}
              />
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
          </Layer>

          {/* 드로잉 레이어 — destination-out 지우개가 이 레이어 내에서만 동작. */}
          <DrawingLinesLayer lines={lines} drawArea={activeRoundRule.drawArea} />
        </Stage>
      )}
    </div>
  )
}
