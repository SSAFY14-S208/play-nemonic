'use client'

import { Group, Rect } from 'react-konva'
import { RELAY_STAGE_SIZE, type RelayRoundArea } from '../../constants'
import DashedGuide from './DashedGuide'
import HintPill from './HintPill'

interface OutgoingHintProps {
  outgoingHintArea: RelayRoundArea
}

export default function OutgoingHint({ outgoingHintArea }: OutgoingHintProps) {
  return (
    <Group>
      <Rect
        x={0}
        y={outgoingHintArea.y}
        width={RELAY_STAGE_SIZE.width}
        height={outgoingHintArea.height}
        fill="#fff1bf"
        opacity={0.36}
        shadowColor="#e5a82f"
        shadowBlur={18}
        shadowOpacity={0.12}
      />
      <DashedGuide verticalPosition={outgoingHintArea.y} />
      <HintPill
        x={46}
        y={outgoingHintArea.y + outgoingHintArea.height - 48}
        label="다음 사람에게 보이는 구간"
      />
    </Group>
  )
}
