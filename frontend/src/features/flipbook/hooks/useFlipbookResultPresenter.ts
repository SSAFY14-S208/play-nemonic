'use client'

import { useCallback, useRef, useState } from 'react'

import { reachFunnelGoal } from '@/shared/libs'
import type { FlipbookResultItemResponse } from '@/shared/types'
import type { FlipbookStep } from '../types'
import { getNormalizedResultItems } from '../utils'

interface UseFlipbookResultPresenterParams {
  onStepChange: (
    step: FlipbookStep,
    options?: {
      roomCode?: string | null
      replace?: boolean
    },
  ) => void
  onLocalStepChange: (step: FlipbookStep) => void
}

interface ShowReadyResultParams {
  resultItems: FlipbookResultItemResponse[]
  resultParticipantCount: number
  targetRoomCode: string | null
  shouldSyncRoute?: boolean
  shouldTrackGoal?: boolean
}

export function useFlipbookResultPresenter({
  onStepChange,
  onLocalStepChange,
}: UseFlipbookResultPresenterParams) {
  const [resultItems, setResultItems] = useState<FlipbookResultItemResponse[]>([])
  const [activeResultIndex, setActiveResultIndex] = useState(0)
  const [isResultReady, setIsResultReady] = useState(false)
  const [resultCount, setResultCount] = useState(0)
  const resultGoalFiredRef = useRef(false)

  const showReadyResult = useCallback(
    ({
      resultItems: readyResultItems,
      resultParticipantCount,
      targetRoomCode,
      shouldSyncRoute = true,
      shouldTrackGoal = true,
    }: ShowReadyResultParams) => {
      const visibleResultItems = getNormalizedResultItems(
        readyResultItems,
        resultParticipantCount,
      )

      setResultItems(visibleResultItems)
      setActiveResultIndex((currentIndex) =>
        Math.min(currentIndex, Math.max(0, visibleResultItems.length - 1)),
      )
      setResultCount(visibleResultItems.length)

      if (shouldTrackGoal && targetRoomCode && !resultGoalFiredRef.current) {
        resultGoalFiredRef.current = true
        reachFunnelGoal('result_viewed', {
          content_type: 'flipbook',
          room_id: targetRoomCode,
        })
      }

      setIsResultReady(true)
      if (shouldSyncRoute && targetRoomCode) {
        onStepChange('result', { roomCode: targetRoomCode })
      } else {
        onLocalStepChange('result')
      }
    },
    [onLocalStepChange, onStepChange],
  )

  const resetResult = useCallback(() => {
    setResultItems([])
    setActiveResultIndex(0)
    setIsResultReady(false)
    setResultCount(0)
    resultGoalFiredRef.current = false
  }, [])

  const selectResult = useCallback(
    (resultIndex: number) => {
      setActiveResultIndex(Math.min(Math.max(0, resultIndex), Math.max(0, resultItems.length - 1)))
    },
    [resultItems.length],
  )

  return {
    activeResultIndex,
    isResultReady,
    resultCount,
    resultItems,
    resetResult,
    selectResult,
    setIsResultReady,
    setResultCount,
    showReadyResult,
  }
}

export type FlipbookResultPresenter = ReturnType<typeof useFlipbookResultPresenter>
