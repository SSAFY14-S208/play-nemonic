import { motion } from 'motion/react'
import Image from 'next/image'

import {
  INTRO_PHASES,
  NEMO_ASSETS_BY_INTRO_PHASE,
  NEMO_TRANSITION,
} from './constants'
import type { ChoreographyPhase } from './types'

export default function NemoCharacters({
  phase,
}: {
  phase: ChoreographyPhase
}) {
  return (
    <>
      {INTRO_PHASES.map((introPhase) => {
        const assets = NEMO_ASSETS_BY_INTRO_PHASE[introPhase]
        const isActive = phase === introPhase
        return (
          <motion.div
            key={`nemo-red-${introPhase}`}
            aria-hidden
            className="pointer-events-none fixed bottom-0 left-4 z-50 sm:left-8"
            initial={{ y: '100%' }}
            animate={{ y: isActive ? '15%' : '100%' }}
            transition={NEMO_TRANSITION}
          >
            <Image
              src={assets.red}
              alt=""
              className="h-40 w-40 sm:h-56 sm:w-56 lg:h-75 lg:w-75"
            />
          </motion.div>
        )
      })}

      {INTRO_PHASES.map((introPhase) => {
        const assets = NEMO_ASSETS_BY_INTRO_PHASE[introPhase]
        const isActive = phase === introPhase
        return (
          <motion.div
            key={`nemo-green-${introPhase}`}
            aria-hidden
            className="pointer-events-none fixed bottom-0 right-4 z-50 sm:right-8"
            initial={{ y: '100%' }}
            animate={{ y: isActive ? '15%' : '100%' }}
            transition={NEMO_TRANSITION}
          >
            <Image
              src={assets.green}
              alt=""
              className="h-40 w-40 sm:h-56 sm:w-56 lg:h-75 lg:w-75"
            />
          </motion.div>
        )
      })}
    </>
  )
}
