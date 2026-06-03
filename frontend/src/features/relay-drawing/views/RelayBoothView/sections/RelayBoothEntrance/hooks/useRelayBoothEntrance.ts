import { useReducedMotion } from 'motion/react'
import { useEffect, useState } from 'react'

import type { ChoreographyPhase } from '../types'

export function useRelayBoothEntrance() {
  const [phase, setPhase] = useState<ChoreographyPhase>('intro-1')
  const [skipped, setSkipped] = useState(false)
  const [completed, setCompleted] = useState(false)
  const prefersReducedMotion = useReducedMotion()

  const isFinalState = skipped || prefersReducedMotion === true || completed

  useEffect(() => {
    if (isFinalState) return
    const handleSkip = () => setSkipped(true)
    window.addEventListener('pointerdown', handleSkip, { once: true })
    return () => window.removeEventListener('pointerdown', handleSkip)
  }, [isFinalState])

  useEffect(() => {
    if (isFinalState) return
    const previousBodyOverflow = document.body.style.overflow
    const previousHtmlOverflow = document.documentElement.style.overflow
    document.body.style.overflow = 'hidden'
    document.documentElement.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previousBodyOverflow
      document.documentElement.style.overflow = previousHtmlOverflow
    }
  }, [isFinalState])

  return {
    phase,
    setPhase,
    isFinalState,
    setCompleted,
  }
}
