import {
  RELAY_FINAL_STAGE_SIZE,
  RELAY_ROUND_ORDER,
  RELAY_ROUND_RULES,
  RELAY_STAGE_SIZE,
  type RelayRoundKey,
} from '../../constants'
import type { RelayRoundLines } from '../../types'

import RoundLineGroup from './RoundLineGroup'

interface CompositeDrawingCanvasProps {
  visibleRoundKeys: RelayRoundKey[]
  roundLines: RelayRoundLines
  isFinalReveal: boolean
  resultImageUrl: string | null
}

// 결과 화면의 SVG 캔버스.
// 서버 합성 이미지(resultImageUrl)가 있으면 <image>로 렌더하고,
// 없으면 로컬 SVG 라인 드로잉으로 fallback 한다.
export default function CompositeDrawingCanvas({
  visibleRoundKeys,
  roundLines,
  isFinalReveal,
  resultImageUrl,
}: CompositeDrawingCanvasProps) {
  const viewBoxHeight = isFinalReveal
    ? RELAY_FINAL_STAGE_SIZE.height
    : RELAY_STAGE_SIZE.height

  // ── 서버 합성 이미지 렌더 ────────────────────────────────────────
  if (resultImageUrl) {
    // 서버 이미지는 848×1920 전체 합성본. 단계별 reveal에서는 해당 라운드
    // 영역만 보이도록 y 오프셋을 잡는다 (viewBox가 720px로 클립).
    const imageOffsetY = isFinalReveal
      ? 0
      : -RELAY_ROUND_RULES[visibleRoundKeys[0]].finalOffsetY

    return (
      <svg
        className="h-full w-full"
        viewBox={`0 0 ${RELAY_STAGE_SIZE.width} ${viewBoxHeight}`}
        role="img"
        aria-label="완성된 릴레이 드로잉"
        preserveAspectRatio="xMidYMid meet"
      >
        <rect width={RELAY_STAGE_SIZE.width} height={viewBoxHeight} fill="#fffdf7" />
        <image
          href={resultImageUrl}
          x={0}
          y={imageOffsetY}
          width={RELAY_STAGE_SIZE.width}
          height={RELAY_FINAL_STAGE_SIZE.height}
        />
      </svg>
    )
  }

  // ── SVG 라인 fallback ────────────────────────────────────────────
  const hasVisibleLines = visibleRoundKeys.some(
    (roundKey) => roundLines[roundKey].length > 0,
  )
  const dotRowCount = Math.ceil(viewBoxHeight / 20)
  // separator는 final 합성에서 각 라운드 drawArea가 시작하는 y 위치에 그린다.
  // drawArea가 캔버스 안에서 어디에 있든(face: 0, body/legs: 120) 최종 좌표계에선
  // finalOffsetY가 그 라운드 drawArea의 시작점이다.
  const separatorPositions = RELAY_ROUND_ORDER.slice(1).map(
    (roundKey) => RELAY_ROUND_RULES[roundKey].finalOffsetY,
  )

  return (
    <svg
      className="h-full w-full"
      viewBox={`0 0 ${RELAY_STAGE_SIZE.width} ${viewBoxHeight}`}
      role="img"
      aria-label="완성된 릴레이 드로잉"
      preserveAspectRatio="xMidYMid meet"
    >
      <rect width={RELAY_STAGE_SIZE.width} height={viewBoxHeight} fill="#fffdf7" />
      {Array.from({ length: dotRowCount }).map((_, rowIndex) =>
        Array.from({ length: 42 }).map((__, columnIndex) => (
          <circle
            key={`${rowIndex}-${columnIndex}`}
            cx={12 + columnIndex * 20}
            cy={12 + rowIndex * 20}
            r={1}
            fill="#ffdc82"
            opacity={0.54}
          />
        )),
      )}

      {isFinalReveal &&
        separatorPositions.map((separatorPosition) => (
          <line
            key={separatorPosition}
            x1={16}
            x2={RELAY_STAGE_SIZE.width - 16}
            y1={separatorPosition}
            y2={separatorPosition}
            stroke="#d49b1f"
            strokeDasharray="7 9"
            opacity={0.45}
          />
        ))}

      {visibleRoundKeys.map((roundKey) => (
        <RoundLineGroup
          key={roundKey}
          roundKey={roundKey}
          lines={roundLines[roundKey]}
          isFinalReveal={isFinalReveal}
        />
      ))}

      {!hasVisibleLines && (
        <text
          x={RELAY_STAGE_SIZE.width / 2}
          y={viewBoxHeight / 2}
          textAnchor="middle"
          dominantBaseline="middle"
          fill="#947c40"
          fontFamily="Paperlogy"
          fontSize={18}
          fontWeight={700}
        >
          아직 저장된 그림이 없어요
        </text>
      )}
    </svg>
  )
}
