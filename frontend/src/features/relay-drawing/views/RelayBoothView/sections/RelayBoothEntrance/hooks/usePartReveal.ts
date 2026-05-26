import { useCallback, useEffect, useRef } from 'react'

import {
  EXPECTED_REVEAL_INDEX_BY_PHASE,
  PART_HOLD_MS,
} from '../constants'
import type { ChoreographyPhase } from '../types'

export function usePartReveal(
  phase: ChoreographyPhase,
  onPhaseChange: (next: ChoreographyPhase) => void,
) {
  const holdTimerRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (holdTimerRef.current === null) return
      window.clearTimeout(holdTimerRef.current)
      holdTimerRef.current = null
    }
  }, [])

  return useCallback(
    (revealedIndex: number) => {
      const expected = EXPECTED_REVEAL_INDEX_BY_PHASE[phase]
      if (expected === null || revealedIndex !== expected) return

      const nextPhase: ChoreographyPhase | null =
        revealedIndex === 0
          ? 'intro-2'
          : revealedIndex === 1
            ? 'intro-3'
            : revealedIndex === 2
              ? 'settling'
              : null
      if (!nextPhase) return

      if (holdTimerRef.current !== null) {
        window.clearTimeout(holdTimerRef.current)
      }
      holdTimerRef.current = window.setTimeout(() => {
        holdTimerRef.current = null
        onPhaseChange(nextPhase)
      }, PART_HOLD_MS)
    },
    [phase, onPhaseChange],
  )
}
