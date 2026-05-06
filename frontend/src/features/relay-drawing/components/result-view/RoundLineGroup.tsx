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
// 최종 합성(isFinalReveal)일 때는 RELAY_ROUND_RULES.finalOffsetY로 세로 이동시켜
// 얼굴/몸통/다리가 한 캔버스에 쌓이도록 한다.
export default function RoundLineGroup({ roundKey, lines, isFinalReveal }: RoundLineGroupProps) {
  const roundRule = RELAY_ROUND_RULES[roundKey]
  const verticalOffset = isFinalReveal ? roundRule.finalOffsetY : 0
  const clipId = `relay-result-${roundKey}-${isFinalReveal ? 'final' : 'single'}`

  return (
    <>
      <defs>
        <clipPath id={clipId}>
          <rect
            x={0}
            y={roundRule.exportArea.y}
            width={RELAY_STAGE_SIZE.width}
            height={roundRule.exportArea.height}
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
