'use client'

import { AnimatePresence, motion } from 'motion/react'
import { RELAY_ROUND_ORDER, type RelayRoundKey } from '../../constants'
import type { TransitionPhase } from '../../hooks'
import TransitionFrame from './TransitionFrame'

interface StickerEntry {
  roundKey: RelayRoundKey
  imageUrl: string
}

interface RoundTransitionOverlayProps {
  isActive: boolean
  phase: TransitionPhase
  phaseProgress: number
  completedRoundKey: RelayRoundKey | null
  nextRoundKey: RelayRoundKey | null
  stickerImageUrl: string | null
  previousStickers: StickerEntry[]
}

const FRAME_HEIGHT = 640
const SLOT_HEIGHT = FRAME_HEIGHT / 3

function computeZoomTransform(nextRoundKey: RelayRoundKey | null) {
  if (!nextRoundKey) return { scale: 1, y: 0 }

  const slotIndex = RELAY_ROUND_ORDER.indexOf(nextRoundKey)
  const slotTop = slotIndex * SLOT_HEIGHT
  const slotCenterY = slotTop + SLOT_HEIGHT / 2
  const frameCenterY = FRAME_HEIGHT / 2

  const zoomScale = 720 / SLOT_HEIGHT
  const zoomOffsetY = (frameCenterY - slotCenterY) * zoomScale

  return { scale: zoomScale, y: zoomOffsetY }
}

export default function RoundTransitionOverlay({
  isActive,
  phase,
  phaseProgress,
  completedRoundKey,
  nextRoundKey,
  stickerImageUrl,
  previousStickers,
}: RoundTransitionOverlayProps) {
  const { scale: zoomScale, y: zoomOffsetY } = computeZoomTransform(nextRoundKey)
  const isZooming = phase === 'zoomIn' || phase === 'fadeIn'
  const overlayOpacity = phase === 'fadeIn' ? 1 - phaseProgress : 1

  return (
    <AnimatePresence>
      {isActive && phase !== 'idle' && phase !== 'fadeOut' && (
        <motion.div
          className="absolute inset-0 z-[var(--z-overlay)] overflow-hidden"
          initial={{ opacity: 0 }}
          animate={{ opacity: overlayOpacity }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
        >
          <motion.div className="absolute inset-0 bg-relay-background/80" />

          <motion.div
            className="flex h-full w-full items-center justify-center"
            animate={
              isZooming
                ? { scale: zoomScale, y: zoomOffsetY }
                : { scale: 1, y: 0 }
            }
            transition={{ duration: 0.4, ease: [0.65, 0, 0.35, 1] }}
          >
            <TransitionFrame
              completedRoundKey={completedRoundKey}
              nextRoundKey={nextRoundKey}
              previousStickers={previousStickers}
              stickerImageUrl={stickerImageUrl}
              showSticker={phase !== 'frameReveal'}
            />
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
