import { useReducedMotion } from 'motion/react'
import { useCallback, useEffect, useState } from 'react'

import { RELAY_ROUND_ORDER } from '../../../../../constants'
import { STRAIGHTEN_HOLD_MS, TILT_HOLD_MS } from '../constants'
import type { RevealPhase } from '../types'

export function useResultRevealAnimation(replayKey: number | string) {
  const prefersReducedMotion = useReducedMotion()
  const [skipped, setSkipped] = useState(false)
  const [phase, setPhase] = useState<RevealPhase>('tilting')
  const [tiltedCount, setTiltedCount] = useState(0)
  const [straightenedCount, setStraightenedCount] = useState(0)

  useEffect(() => {
    const animationFrameId = requestAnimationFrame(() => {
      setSkipped(false)
      setPhase('tilting')
      setTiltedCount(0)
      setStraightenedCount(0)
    })
    return () => cancelAnimationFrame(animationFrameId)
  }, [replayKey])

  useEffect(() => {
    if (!prefersReducedMotion) return
    const animationFrameId = requestAnimationFrame(() => setSkipped(true))
    return () => cancelAnimationFrame(animationFrameId)
  }, [prefersReducedMotion])

  useEffect(() => {
    if (skipped) return
    if (phase !== 'tilting') return
    if (tiltedCount < RELAY_ROUND_ORDER.length) return
    const timerId = window.setTimeout(() => {
      setPhase('straightening')
    }, TILT_HOLD_MS)
    return () => window.clearTimeout(timerId)
  }, [phase, skipped, tiltedCount])

  useEffect(() => {
    if (skipped) return
    if (phase !== 'straightening') return
    if (straightenedCount < RELAY_ROUND_ORDER.length) return
    const timerId = window.setTimeout(() => {
      setPhase('overlay')
    }, STRAIGHTEN_HOLD_MS)
    return () => window.clearTimeout(timerId)
  }, [phase, skipped, straightenedCount])

  const handleTiltLanded = useCallback(() => {
    setTiltedCount((current) => current + 1)
  }, [])

  const handleStraightened = useCallback(() => {
    setStraightenedCount((current) => current + 1)
  }, [])

  const handleSkip = useCallback(() => {
    setSkipped(true)
  }, [])

  return {
    skipped,
    phase,
    setPhase,
    handleTiltLanded,
    handleStraightened,
    handleSkip,
  }
}
