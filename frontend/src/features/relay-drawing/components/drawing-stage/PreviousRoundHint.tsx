'use client'

import { Group, Line, Rect, Text } from 'react-konva'
import { RELAY_STAGE_SIZE, type RelayRoundArea } from '../../constants'
import type { RelayDrawLine } from '../../useRelayDrawing'
import DashedGuide from './DashedGuide'
import HintPill from './HintPill'

interface PreviousRoundHintProps {
  hintTargetArea: RelayRoundArea
  hintVerticalOffset: number
  previousRoundLines: RelayDrawLine[]
}

export default function PreviousRoundHint({
  hintTargetArea,
  hintVerticalOffset,
  previousRoundLines,
}: PreviousRoundHintProps) {
  return (
    <Group>
      <Rect
        x={0}
        y={hintTargetArea.y}
        width={RELAY_STAGE_SIZE.width}
        height={hintTargetArea.height}
        fill="#fff8e4"
        opacity={0.72}
        shadowColor="#e5a82f"
        shadowBlur={18}
        shadowOpacity={0.16}
      />
      <HintPill
        x={46}
        y={hintTargetArea.y + 18}
        label="이전 사람의 그림 (하단 일부)"
      />
      <Group
        clipX={0}
        clipY={hintTargetArea.y}
        clipWidth={RELAY_STAGE_SIZE.width}
        clipHeight={hintTargetArea.height}
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
      <DashedGuide verticalPosition={hintTargetArea.y + hintTargetArea.height} />
      <Text
        x={0}
        y={hintTargetArea.y + 76}
        width={RELAY_STAGE_SIZE.width}
        text="↓ 여기부터 이어 그리세요"
        align="center"
        fontFamily="Pretendard Variable"
        fontSize={16}
        fontStyle="bold"
        fill="#efc759"
        opacity={0.72}
      />
    </Group>
  )
}
