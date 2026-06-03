'use client'

import Image from 'next/image'
import { AnimatePresence, motion } from 'motion/react'

import { relayDrawingGameStart } from '@/features/relay-drawing/assets'
import type { RelayRoomViewState } from '../../hooks'

interface GameStartOverlayProps {
  gameStartPhase: RelayRoomViewState['gameStartPhase']
  onImageShown: () => void
}

export default function GameStartOverlay({
  gameStartPhase,
  onImageShown,
}: GameStartOverlayProps) {
  return (
    <AnimatePresence>
      {gameStartPhase === 'animating' && (
        <motion.div
          key="game-start-overlay"
          className="fixed inset-0 z-30 grid place-items-center"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.3 }}
        >
          <motion.div
            initial={{ scale: 0.3, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{
              delay: 0.35,
              duration: 0.5,
              ease: [0.34, 1.56, 0.64, 1],
            }}
            onAnimationComplete={onImageShown}
          >
            <Image
              src={relayDrawingGameStart}
              alt="릴레이 드로잉 시작!"
              className="h-auto w-[min(90vw,600px)]"
              priority
            />
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
