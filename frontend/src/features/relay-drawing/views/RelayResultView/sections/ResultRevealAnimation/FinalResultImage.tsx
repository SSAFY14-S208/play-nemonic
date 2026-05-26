import { motion } from 'motion/react'

import {
  RELAY_FINAL_STAGE_SIZE,
  RELAY_STAGE_SIZE,
} from '../../../../constants'
import LabelPaperCard from '../../../../components/LabelPaperCard'
import type { RevealPhase } from './types'

interface FinalResultImageProps {
  replayKey: number | string
  resultImageUrl: string
  skipped: boolean
  isOverlayActive: boolean
  phase: RevealPhase
  onFinal: () => void
}

export default function FinalResultImage({
  replayKey,
  resultImageUrl,
  skipped,
  isOverlayActive,
  phase,
  onFinal,
}: FinalResultImageProps) {
  return (
    <motion.div
      key={`final-${replayKey}`}
      className="absolute inset-5"
      initial={{ opacity: 0 }}
      animate={{ opacity: isOverlayActive ? 1 : 0 }}
      transition={
        skipped ? { duration: 0 } : { duration: 0.55, delay: 0.1, ease: 'easeOut' }
      }
      onAnimationComplete={() => {
        if (isOverlayActive && phase !== 'final') onFinal()
      }}
    >
      <LabelPaperCard className="h-full w-full">
        <svg
          className="block h-full w-full"
          viewBox={`0 0 ${RELAY_STAGE_SIZE.width} ${RELAY_FINAL_STAGE_SIZE.height}`}
          preserveAspectRatio="xMidYMid meet"
          role="img"
          aria-label="완성된 릴레이 드로잉"
        >
          <image
            href={resultImageUrl}
            x={0}
            y={0}
            width={RELAY_STAGE_SIZE.width}
            height={RELAY_FINAL_STAGE_SIZE.height}
          />
        </svg>
      </LabelPaperCard>
    </motion.div>
  )
}
