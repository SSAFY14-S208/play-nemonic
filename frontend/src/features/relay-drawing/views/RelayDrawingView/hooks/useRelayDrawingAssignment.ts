'use client'

import { useEffect, useRef } from 'react'

import { getRelayRoomAssignmentMe } from '@/shared/apis'
import type { RelayRoomStatus } from '@/shared/types'

import { PART_TO_ROUND_KEY } from '@/features/relay-drawing/constants'
import { useRelayDrawingStore } from '@/features/relay-drawing/stores'

const ASSIGNMENT_RETRY_DELAYS_MS = [1000, 2000, 3000, 5000]

interface UseRelayDrawingAssignmentParams {
  roomCode: string | null
  roomStatus: RelayRoomStatus | null
  partFetchTrigger: number
}

export function useRelayDrawingAssignment({
  roomCode,
  roomStatus,
  partFetchTrigger,
}: UseRelayDrawingAssignmentParams) {
  const setAssignment = useRelayDrawingStore((state) => state.setAssignment)
  const fetchingPartRef = useRef<string | null>(null)

  useEffect(() => {
    if (roomStatus !== 'PLAYING' || !roomCode) return

    const fetchKey = `${roomCode}-${partFetchTrigger}`
    if (fetchingPartRef.current === fetchKey) return

    fetchingPartRef.current = fetchKey
    let cancelled = false

    void (async () => {
      const maxAttempts = 1 + ASSIGNMENT_RETRY_DELAYS_MS.length

      for (let attempt = 0; attempt < maxAttempts; attempt++) {
        if (cancelled) return

        try {
          const assignment = await getRelayRoomAssignmentMe(roomCode)
          if (cancelled) return

          setAssignment(assignment)
          if (assignment.assignmentStatus !== 'PENDING') {
            const roundKey = PART_TO_ROUND_KEY[assignment.part]
            useRelayDrawingStore.getState().markSubmitted(roundKey)
          }
          return
        } catch {
          if (attempt < ASSIGNMENT_RETRY_DELAYS_MS.length) {
            await new Promise((resolve) =>
              setTimeout(resolve, ASSIGNMENT_RETRY_DELAYS_MS[attempt]),
            )
          }
        }
      }
    })()

    return () => {
      cancelled = true
      fetchingPartRef.current = null
    }
  }, [roomStatus, roomCode, partFetchTrigger, setAssignment])
}
