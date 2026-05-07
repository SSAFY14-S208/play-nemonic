'use client'

import { useCallback, useMemo, useState } from 'react'
import {
  FLIPBOOK_PARTICIPANTS,
  FLIPBOOK_TIME_LIMITS_SECONDS,
  type FlipbookTimeLimitSeconds,
} from '../constants'
import { createFlipbookSettings } from '../utils'
import type { useFlipbookRealtimeActions } from './useFlipbookRealtimeActions'

interface UseFlipbookSettingsOptions {
  minimumRoundCount: number
  realtimeActions: ReturnType<typeof useFlipbookRealtimeActions>
}

export function useFlipbookSettings({
  minimumRoundCount,
  realtimeActions,
}: UseFlipbookSettingsOptions) {
  const [selectedTimeLimitSeconds, setSelectedTimeLimitSeconds] = useState<
    FlipbookTimeLimitSeconds
  >(FLIPBOOK_TIME_LIMITS_SECONDS[1])
  const [roundCount, setRoundCount] = useState(Math.max(5, minimumRoundCount))
  const settings = useMemo(
    () =>
      createFlipbookSettings({
        minimumRoundCount,
        participantCount: FLIPBOOK_PARTICIPANTS.length,
        roundCount,
        selectedTimeLimitSeconds,
      }),
    [minimumRoundCount, roundCount, selectedTimeLimitSeconds],
  )

  const increaseRoundCount = useCallback(() => {
    const nextRoundCount = roundCount + 1

    setRoundCount(nextRoundCount)
    realtimeActions.enqueueSettingsUpdate(
      createFlipbookSettings({
        minimumRoundCount,
        participantCount: FLIPBOOK_PARTICIPANTS.length,
        roundCount: nextRoundCount,
        selectedTimeLimitSeconds,
      }),
    )
  }, [minimumRoundCount, realtimeActions, roundCount, selectedTimeLimitSeconds])

  const decreaseRoundCount = useCallback(() => {
    const nextRoundCount = Math.max(minimumRoundCount, roundCount - 1)

    setRoundCount(nextRoundCount)
    realtimeActions.enqueueSettingsUpdate(
      createFlipbookSettings({
        minimumRoundCount,
        participantCount: FLIPBOOK_PARTICIPANTS.length,
        roundCount: nextRoundCount,
        selectedTimeLimitSeconds,
      }),
    )
  }, [minimumRoundCount, realtimeActions, roundCount, selectedTimeLimitSeconds])

  const selectTimeLimit = useCallback(
    (timeLimitSeconds: FlipbookTimeLimitSeconds) => {
      setSelectedTimeLimitSeconds(timeLimitSeconds)
      realtimeActions.enqueueSettingsUpdate(
        createFlipbookSettings({
          minimumRoundCount,
          participantCount: FLIPBOOK_PARTICIPANTS.length,
          roundCount,
          selectedTimeLimitSeconds: timeLimitSeconds,
        }),
      )
    },
    [minimumRoundCount, realtimeActions, roundCount],
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
