'use client'

import { useCallback, useEffect, useState } from 'react'
import { useReducedMotion } from 'motion/react'

type FlipbookEntranceIntroPhase = 'playing' | 'actions' | 'done'

const ACTION_REVEAL_DELAY_MS = 2200
const INTRO_DONE_AFTER_ACTION_MS = 650

export function useFlipbookEntranceIntro() {
  const shouldReduceMotion = useReducedMotion() ?? false
  const [introPhase, setIntroPhase] = useState<FlipbookEntranceIntroPhase>('playing')

  const skipIntro = useCallback(() => {
    setIntroPhase('done')
  }, [])

  useEffect(() => {
    if (introPhase !== 'playing') return

    const handleSkipInput = () => {
      skipIntro()
    }

    window.addEventListener('keydown', handleSkipInput)
    window.addEventListener('pointerdown', handleSkipInput, { capture: true })

    return () => {
      window.removeEventListener('keydown', handleSkipInput)
      window.removeEventListener('pointerdown', handleSkipInput, { capture: true })
    }
  }, [introPhase, skipIntro])

  useEffect(() => {
    if (introPhase !== 'playing') return

    const actionRevealDelay = shouldReduceMotion ? 0 : ACTION_REVEAL_DELAY_MS
    const actionRevealTimer = window.setTimeout(() => {
      setIntroPhase((currentPhase) => (currentPhase === 'playing' ? 'actions' : currentPhase))
    }, actionRevealDelay)

    return () => {
      window.clearTimeout(actionRevealTimer)
    }
  }, [introPhase, shouldReduceMotion])

  useEffect(() => {
    if (introPhase !== 'actions') return

    const introDoneDelay = shouldReduceMotion ? 0 : INTRO_DONE_AFTER_ACTION_MS
    const introDoneTimer = window.setTimeout(() => {
      setIntroPhase('done')
    }, introDoneDelay)

    return () => {
      window.clearTimeout(introDoneTimer)
    }
  }, [introPhase, shouldReduceMotion])

  return {
    introPhase,
    isIntroComplete: introPhase === 'done',
    isActionVisible: introPhase !== 'playing',
    skipIntro,
  }
}
