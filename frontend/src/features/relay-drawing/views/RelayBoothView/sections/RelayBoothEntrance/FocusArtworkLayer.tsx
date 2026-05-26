import { motion } from 'motion/react'

import RelayArtworkCard from '../../../../components/RelayArtworkCard'
import { CAMERA_PAN_TRANSITION, PART_GAP } from './constants'
import type { Measurement } from './types'

interface FocusArtworkLayerProps {
  isSettled: boolean
  focusIndex: number
  revealCount: number
  measurement: Measurement
  onPartReveal: (revealedIndex: number) => void
}

export default function FocusArtworkLayer({
  isSettled,
  focusIndex,
  revealCount,
  measurement,
  onPartReveal,
}: FocusArtworkLayerProps) {
  return (
    <motion.div
      className="relative z-30"
      initial={{
        scale: measurement.introScale,
        y: (measurement.partHeight + PART_GAP) * measurement.introScale,
      }}
      animate={
        isSettled
          ? { scale: 1, y: 0 }
          : {
              scale: measurement.introScale,
              y:
                (measurement.partHeight + PART_GAP) *
                (1 - focusIndex) *
                measurement.introScale,
            }
      }
      transition={CAMERA_PAN_TRANSITION}
    >
      <RelayArtworkCard
        variant={2}
        revealCount={revealCount}
        onPartReveal={onPartReveal}
        size={measurement.partWidth}
      />
    </motion.div>
  )
}
