import { motion } from 'motion/react'

import {
  RELAY_FINAL_STAGE_SIZE,
  RELAY_ROUND_RULES,
  RELAY_STAGE_SIZE,
  type RelayRoundKey,
} from '../../../../constants'
import LabelPaperCard from '../../../../components/LabelPaperCard'
import { memoVariants } from './animationVariants'
import { getSliceTopPct, SLICE_HEIGHT_PCT } from './constants'

interface MemoSliceProps {
  roundKey: RelayRoundKey
  resultImageUrl: string
  tiltDeg: number
  onTilted: () => void
  onStraightened: () => void
}

export default function MemoSlice({
  roundKey,
  resultImageUrl,
  tiltDeg,
  onTilted,
  onStraightened,
}: MemoSliceProps) {
  return (
    <motion.div
      custom={tiltDeg}
      variants={memoVariants}
      className="absolute inset-x-0"
      style={{
        top: `${getSliceTopPct(roundKey)}%`,
        height: `${SLICE_HEIGHT_PCT}%`,
      }}
      onAnimationComplete={(definition) => {
        if (definition === 'tilted') onTilted()
        else if (definition === 'straight') onStraightened()
      }}
    >
      <LabelPaperCard className="h-full w-full">
        <svg
          className="block h-full w-full"
          viewBox={`0 0 ${RELAY_STAGE_SIZE.width} ${RELAY_STAGE_SIZE.height}`}
          preserveAspectRatio="xMidYMid meet"
          role="img"
          aria-label={`${RELAY_ROUND_RULES[roundKey].label} 슬라이스`}
        >
          <image
            href={resultImageUrl}
            x={0}
            y={-RELAY_ROUND_RULES[roundKey].finalOffsetY}
            width={RELAY_STAGE_SIZE.width}
            height={RELAY_FINAL_STAGE_SIZE.height}
          />
        </svg>
      </LabelPaperCard>
    </motion.div>
  )
}
