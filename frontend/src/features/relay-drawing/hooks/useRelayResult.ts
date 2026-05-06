'use client'

import { useMemo } from 'react'
import {
  RELAY_RESULT_REVEALS,
  RELAY_ROUND_ORDER,
  RELAY_ROUND_RULES,
  type RelayRoundKey,
} from '../constants'
import { useRelayDrawingStore } from '../relayDrawingStore'
import type { RelayCompositeDrawingPayload, RelayDrawLine } from '../types'

function moveLineToFinalPosition(line: RelayDrawLine, roundKey: RelayRoundKey): RelayDrawLine {
  const roundRule = RELAY_ROUND_RULES[roundKey]

  return {
    ...line,
    id: `${roundKey}-${line.id}`,
    points: line.points.map((point) => ({
      x: point.x,
      y: point.y + roundRule.finalOffsetY,
    })),
  }
}

export function useRelayResult() {
  const resultRevealStep = useRelayDrawingStore((state) => state.resultRevealStep)
  const roundLines = useRelayDrawingStore((state) => state.roundLines)
  const completedAt = useRelayDrawingStore((state) => state.completedAt)
  const goToNextResultReveal = useRelayDrawingStore((state) => state.goToNextResultReveal)
  const goToPreviousResultReveal = useRelayDrawingStore((state) => state.goToPreviousResultReveal)

  const currentResultRevealIndex = RELAY_RESULT_REVEALS.findIndex(
    (reveal) => reveal.key === resultRevealStep,
  )
  const canShowPreviousResultReveal = currentResultRevealIndex > 0
  const canShowNextResultReveal = currentResultRevealIndex < RELAY_RESULT_REVEALS.length - 1

  const compositeDrawingPayload = useMemo<RelayCompositeDrawingPayload>(
    () => ({
      rounds: roundLines,
      mergedLines: RELAY_ROUND_ORDER.flatMap((roundKey) =>
        roundLines[roundKey].map((line) => moveLineToFinalPosition(line, roundKey)),
      ),
      completedAt,
    }),
    [completedAt, roundLines],
  )

  return {
    resultRevealStep,
    canShowPreviousResultReveal,
    canShowNextResultReveal,
    goToNextResultReveal,
    goToPreviousResultReveal,
    compositeDrawingPayload,
  }
}
