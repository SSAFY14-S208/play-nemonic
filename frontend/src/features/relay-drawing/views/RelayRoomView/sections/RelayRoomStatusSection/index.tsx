'use client'

import { AnimatePresence, motion } from 'motion/react'

import type { RelayRoomViewState } from '../../hooks'
import RelayDrawingView from '../../../RelayDrawingView'
import RelayFinalizingView from '../../../RelayFinalizingView'
import RelayLobbyView from '../../../RelayLobbyView'
import RelayResultView from '../../../RelayResultView'

interface RelayRoomStatusSectionProps {
  roomStatus: RelayRoomViewState['roomStatus']
  gameStartPhase: RelayRoomViewState['gameStartPhase']
}

export default function RelayRoomStatusSection({
  roomStatus,
  gameStartPhase,
}: RelayRoomStatusSectionProps) {
  return (
    <div className="mx-auto w-full max-w-300">
      <AnimatePresence mode="wait">
        {roomStatus === 'PLAYING' && gameStartPhase === 'idle' ? (
          <motion.div
            key="drawing"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.35 }}
          >
            <RelayDrawingView />
          </motion.div>
        ) : roomStatus === 'FINALIZING' ? (
          <motion.div
            key="finalizing"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3 }}
          >
            <RelayFinalizingView />
          </motion.div>
        ) : roomStatus === 'FINISHED' ? (
          <motion.div
            key="finished"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3 }}
          >
            <RelayResultView />
          </motion.div>
        ) : (
          <motion.div
            key="lobby"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, transition: { duration: 0 } }}
            transition={{ duration: 0.55 }}
          >
            <RelayLobbyView />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
