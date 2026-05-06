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
}

// 결과 화면의 SVG 캔버스 — 단계별로 보일 라운드(visibleRoundKeys)만 그리고,
// 최종 단계에선 세로 합성된 1장 그림을 그린다. 빈 라운드는 안내 텍스트.
export default function CompositeDrawingCanvas({
  visibleRoundKeys,
  roundLines,
  isFinalReveal,
}: CompositeDrawingCanvasProps) {
  const hasVisibleLines = visibleRoundKeys.some(
    (roundKey) => roundLines[roundKey].length > 0,
  )
  const viewBoxHeight = isFinalReveal
    ? RELAY_FINAL_STAGE_SIZE.height
    : RELAY_STAGE_SIZE.height
  const dotRowCount = Math.ceil(viewBoxHeight / 20)
  const separatorPositions = RELAY_ROUND_ORDER.slice(1).map(
    (roundKey) =>
      RELAY_ROUND_RULES[roundKey].finalOffsetY +
      RELAY_ROUND_RULES[roundKey].drawArea.y,
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
          fontFamily="Pretendard Variable"
          fontSize={18}
          fontWeight={700}
        >
          아직 저장된 그림이 없어요
        </text>
      )}
    </svg>
  )
}
