import { motion } from 'motion/react'

import RelayArtworkCard from '@/features/relay-drawing/components/RelayArtworkCard'
import { SIDE_ROTATION_TRANSITION } from './constants'
import type { FanTarget } from './types'

interface SideArtworkLayerProps {
  className: string
  isFanning: boolean
  target: FanTarget
  variant: 1 | 3
  size: number
  onFanningComplete?: () => void
}

export default function SideArtworkLayer({
  className,
  isFanning,
  target,
  variant,
  size,
  onFanningComplete,
}: SideArtworkLayerProps) {
  return (
    <motion.div
      className={className}
      initial={{ rotate: 0, x: 0, opacity: 0 }}
      animate={{
        rotate: isFanning ? target.rotate : 0,
        x: isFanning ? target.x : 0,
        opacity: isFanning ? 1 : 0,
      }}
      transition={SIDE_ROTATION_TRANSITION}
      onAnimationComplete={() => {
        if (isFanning) onFanningComplete?.()
      }}
    >
      <RelayArtworkCard variant={variant} size={size} />
    </motion.div>
  )
}
