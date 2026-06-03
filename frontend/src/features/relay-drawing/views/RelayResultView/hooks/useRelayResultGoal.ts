'use client'

import { useEffect, useRef } from 'react'

import { reachFunnelGoal } from '@/shared/libs'

interface UseRelayResultGoalParams {
  hasServerResults: boolean
  roomCode: string | null
}

export function useRelayResultGoal({ hasServerResults, roomCode }: UseRelayResultGoalParams) {
  const goalFiredRef = useRef(false)

  useEffect(() => {
    if (!hasServerResults || goalFiredRef.current) return

    goalFiredRef.current = true
    reachFunnelGoal('result_viewed', {
      content_type: 'relay',
      room_id: roomCode ?? undefined,
    })
  }, [hasServerResults, roomCode])
}
