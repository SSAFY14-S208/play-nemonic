import {
  RELAY_ROUND_RULES,
  RELAY_STAGE_SIZE,
  type RelayRoundKey,
} from '../../constants'
import type { RelayDrawLine } from '../../types'

interface RoundLineGroupProps {
  roundKey: RelayRoundKey
  lines: RelayDrawLine[]
  isFinalReveal: boolean
}

// 한 라운드의 라인을 SVG group으로 그린다.
// 최종 합성(isFinalReveal)일 때는 (point.y - drawArea.y) + finalOffsetY 위치로
// 옮겨서 얼굴/몸통/다리가 한 캔버스에 쌓이도록 한다.
// 단일 라운드 reveal일 때는 drawArea를 viewBox 0..720으로 보여주려 -drawArea.y만큼 옮긴다.
export default function RoundLineGroup({ roundKey, lines, isFinalReveal }: RoundLineGroupProps) {
  const roundRule = RELAY_ROUND_RULES[roundKey]
  const verticalOffset = isFinalReveal
    ? roundRule.finalOffsetY - roundRule.drawArea.y
    : -roundRule.drawArea.y
  const clipId = `relay-result-${roundKey}-${isFinalReveal ? 'final' : 'single'}`

  // clipPath는 user space(부모 좌표계) 기준이라 transform 후 라인이 가는 위치
  // 그대로 직사각형을 잡아야 한다.
  // - single reveal: 라인이 svg y=0..drawArea.h 영역에 깔린다.
  // - final reveal: 라인이 svg y=finalOffsetY..(finalOffsetY+drawArea.h)에 깔린다.
  const clipY = isFinalReveal ? roundRule.finalOffsetY : 0
  const clipHeight = roundRule.drawArea.height

  return (
    <>
      <defs>
        <clipPath id={clipId}>
          <rect
            x={0}
            y={clipY}
            width={RELAY_STAGE_SIZE.width}
            height={clipHeight}
          />
        </clipPath>
      </defs>
      <g clipPath={`url(#${clipId})`} transform={`translate(0 ${verticalOffset})`}>
        {lines.map((line) => {
          if (line.kind === 'fill') {
            if (line.imageDataUrl) {
              return (
                <image
                  key={line.id}
                  href={line.imageDataUrl}
                  x={0}
                  y={0}
                  width={RELAY_STAGE_SIZE.width}
                  height={RELAY_STAGE_SIZE.height}
                />
              )
            }

            return (
              <polygon
                key={line.id}
                points={line.points.map((point) => `${point.x},${point.y}`).join(' ')}
                fill={line.color}
              />
            )
          }

          return (
            <polyline
              key={line.id}
              points={line.points.map((point) => `${point.x},${point.y}`).join(' ')}
              fill="none"
              stroke={line.color}
              strokeWidth={line.strokeWidth}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          )
        })}
      </g>
    </>
  )
}
