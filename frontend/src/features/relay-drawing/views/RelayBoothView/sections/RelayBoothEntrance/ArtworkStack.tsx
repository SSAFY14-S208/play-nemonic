import { motion } from 'motion/react'

import {
  FOCUS_INDEX_BY_PHASE,
  REVEAL_COUNT_BY_PHASE,
  SLOT_TRANSITION,
} from './constants'
import FocusArtworkLayer from './FocusArtworkLayer'
import SideArtworkLayer from './SideArtworkLayer'
import type { ChoreographyPhase, Measurement } from './types'

interface ArtworkStackProps {
  phase: ChoreographyPhase
  measurement: Measurement
  onPhaseChange: (next: ChoreographyPhase) => void
  onComplete: () => void
  onLeftReveal: () => void
  onPartReveal: (revealedIndex: number) => void
}

export default function ArtworkStack({
  phase,
  measurement,
  onPhaseChange,
  onComplete,
  onLeftReveal,
  onPartReveal,
}: ArtworkStackProps) {
  const isSettled = phase === 'settling' || phase === 'fanning'
  const isFanning = phase === 'fanning'
  const focusIndex = FOCUS_INDEX_BY_PHASE[phase]
  const revealCount = REVEAL_COUNT_BY_PHASE[phase]

  return (
    <motion.div
      className="relative"
      initial={{
        x: measurement.centerOffset.x,
        y: measurement.centerOffset.y,
      }}
      animate={
        isSettled
          ? { x: 0, y: 0 }
          : { x: measurement.centerOffset.x, y: measurement.centerOffset.y }
      }
      transition={SLOT_TRANSITION}
      onAnimationComplete={() => {
        if (phase !== 'settling') return
        onPhaseChange('fanning')
        onLeftReveal()
      }}
    >
      <SideArtworkLayer
        className="absolute inset-0 z-10 origin-bottom"
        isFanning={isFanning}
        target={measurement.fanRight}
        variant={1}
        size={measurement.partWidth}
        onFanningComplete={onComplete}
      />
      <SideArtworkLayer
        className="absolute inset-0 z-20 origin-bottom"
        isFanning={isFanning}
        target={measurement.fanLeft}
        variant={3}
        size={measurement.partWidth}
      />
      <FocusArtworkLayer
        isSettled={isSettled}
        focusIndex={focusIndex}
        revealCount={revealCount}
        measurement={measurement}
        onPartReveal={onPartReveal}
      />
    </motion.div>
  )
}
