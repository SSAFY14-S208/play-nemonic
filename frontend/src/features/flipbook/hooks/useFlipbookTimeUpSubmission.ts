'use client'

import { useEffect, type Dispatch, type SetStateAction } from 'react'

import type { FlipbookAssignmentResponse } from '@/shared/types'
import type { FlipbookTimeUpSubmitRequest } from '../types'
import { getAssignmentKey } from '../utils'

interface UseFlipbookTimeUpSubmissionParams {
  assignment: FlipbookAssignmentResponse | null
  completeRound: (options: { keepSubmittingUntilServerAdvance?: boolean }) => Promise<void>
  roomCode: string | null
  setTimeUpSubmitRequest: Dispatch<SetStateAction<FlipbookTimeUpSubmitRequest | null>>
  timeUpSubmitRequest: FlipbookTimeUpSubmitRequest | null
}

export function useFlipbookTimeUpSubmission({
  assignment,
  completeRound,
  roomCode,
  setTimeUpSubmitRequest,
  timeUpSubmitRequest,
}: UseFlipbookTimeUpSubmissionParams) {
  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (!timeUpSubmitRequest || !assignment) return
      const isCurrentTimeUpRequest =
        timeUpSubmitRequest.roomCode === roomCode &&
        timeUpSubmitRequest.round === assignment.currentRound &&
        timeUpSubmitRequest.assignmentKey === getAssignmentKey(assignment) &&
        timeUpSubmitRequest.roundDeadlineAt === assignment.roundDeadlineAt

      if (!isCurrentTimeUpRequest) {
        if (!cancelled) {
          setTimeUpSubmitRequest(null)
        }
        return
      }

      await completeRound({ keepSubmittingUntilServerAdvance: true })
      if (!cancelled) {
        setTimeUpSubmitRequest(null)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [assignment, completeRound, roomCode, setTimeUpSubmitRequest, timeUpSubmitRequest])
}
