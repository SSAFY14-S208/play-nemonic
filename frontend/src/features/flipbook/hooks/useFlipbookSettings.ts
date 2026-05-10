'use client'

import { useCallback, useMemo, useState } from 'react'
import { FLIPBOOK_TIME_LIMITS_SECONDS } from '../constants'
import { createFlipbookSettings } from '../flipbookSessionMapper'
import type { FlipbookTimeLimitSeconds } from '../types'
import type { useFlipbookRealtimeActions } from './useFlipbookRealtimeActions'

interface UseFlipbookSettingsOptions {
  minimumRoundCount: number
  participantCount: number
  realtimeActions: ReturnType<typeof useFlipbookRealtimeActions>
}

export function useFlipbookSettings({
  minimumRoundCount,
  participantCount,
  realtimeActions,
}: UseFlipbookSettingsOptions) {
  const [selectedTimeLimitSeconds, setSelectedTimeLimitSeconds] = useState<
    FlipbookTimeLimitSeconds
  >(FLIPBOOK_TIME_LIMITS_SECONDS[1])
  const [roundCount, setRoundCount] = useState(minimumRoundCount)
  const settings = useMemo(
    () =>
      createFlipbookSettings({
        minimumRoundCount,
        participantCount,
        roundCount,
        selectedTimeLimitSeconds,
      }),
    [minimumRoundCount, participantCount, roundCount, selectedTimeLimitSeconds],
  )

  const increaseRoundCount = useCallback(() => {
    const nextRoundCount = roundCount + 1

    setRoundCount(nextRoundCount)
    realtimeActions.enqueueSettingsUpdate(
      createFlipbookSettings({
        minimumRoundCount,
        participantCount,
        roundCount: nextRoundCount,
        selectedTimeLimitSeconds,
      }),
    )
  }, [minimumRoundCount, participantCount, realtimeActions, roundCount, selectedTimeLimitSeconds])

  const decreaseRoundCount = useCallback(() => {
    const nextRoundCount = Math.max(minimumRoundCount, roundCount - 1)

    setRoundCount(nextRoundCount)
    realtimeActions.enqueueSettingsUpdate(
      createFlipbookSettings({
        minimumRoundCount,
        participantCount,
        roundCount: nextRoundCount,
        selectedTimeLimitSeconds,
      }),
    )
  }, [minimumRoundCount, participantCount, realtimeActions, roundCount, selectedTimeLimitSeconds])

  const selectTimeLimit = useCallback(
    (timeLimitSeconds: FlipbookTimeLimitSeconds) => {
      setSelectedTimeLimitSeconds(timeLimitSeconds)
      realtimeActions.enqueueSettingsUpdate(
        createFlipbookSettings({
          minimumRoundCount,
          participantCount,
          roundCount,
          selectedTimeLimitSeconds: timeLimitSeconds,
        }),
      )
    },
    [minimumRoundCount, participantCount, realtimeActions, roundCount],
  )

  return {
    selectedTimeLimitSeconds,
    roundCount,
    settings,
    increaseRoundCount,
    decreaseRoundCount,
    selectTimeLimit,
  }
}
