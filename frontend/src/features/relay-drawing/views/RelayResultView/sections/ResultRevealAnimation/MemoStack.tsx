import { motion } from 'motion/react'

import { RELAY_ROUND_ORDER } from '../../../../constants'
import { stackVariants } from './animationVariants'
import { TILT_BY_ROUND } from './constants'
import MemoSlice from './MemoSlice'

interface MemoStackProps {
  replayKey: number | string
  resultImageUrl: string
  stackTarget: 'tilted' | 'straight'
  onTilted: () => void
  onStraightened: () => void
}

export default function MemoStack({
  replayKey,
  resultImageUrl,
  stackTarget,
  onTilted,
  onStraightened,
}: MemoStackProps) {
  return (
    <motion.div
      key={`stack-${replayKey}`}
      className="absolute inset-5"
      initial="hidden"
      animate={stackTarget}
      variants={stackVariants}
    >
      {RELAY_ROUND_ORDER.map((roundKey) => (
        <MemoSlice
          key={roundKey}
          roundKey={roundKey}
          resultImageUrl={resultImageUrl}
          tiltDeg={TILT_BY_ROUND[roundKey]}
          onTilted={onTilted}
          onStraightened={onStraightened}
        />
      ))}
    </motion.div>
  )
}
