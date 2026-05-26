'use client'

import { useCallback, useEffect, useState } from 'react'
import { useRelayDrawingStore } from '../../../stores'

const EXPIRING_THRESHOLD_SECONDS = 10
// 매 250ms tick — 1초 단위 표시 정확도와 deadline 도달 감지 지연을 동시에 잡기 위함.
const TICK_INTERVAL_MS = 250

interface UseRelayTimerReturn {
  remainingSeconds: number
  isExpiring: boolean
  formattedTime: string
}

/**
 * 드로잉 라운드 카운트다운 타이머.
 *
 * `partDeadlineAt`(서버가 보내는 절대 시각)을 단일 진실의 기준으로 두고,
 * 매 tick마다 `Date.now()` 와 비교해 남은 초를 재계산한다. 로컬 카운터를
 * 누적해서 줄이는 방식이 아니므로 다음 문제들이 발생하지 않는다:
 *   - 라운드 전환 시 stale 0초가 다음 라운드의 effect 의존성에 흘러드는 race
 *   - 탭 비활성/throttle 후 시각이 어긋나는 drift
 *   - 시스템/클럭 변경 시의 누적 오차
 *
 * deadline이 없으면(GAME_STARTED 전 lobby/preview 등) `timeLimitSeconds` 폴백.
 */
export function useRelayTimer(): UseRelayTimerReturn {
  const timeLimitSeconds = useRelayDrawingStore((state) => state.timeLimitSeconds)
  const partDeadlineAt = useRelayDrawingStore((state) => state.partDeadlineAt)
  const roomStatus = useRelayDrawingStore((state) => state.roomStatus)

  const computeRemaining = useCallback(() => {
    if (!partDeadlineAt) return timeLimitSeconds
    // 백엔드가 timezone-aware ISO-8601 문자열로 deadline을 보내므로 그대로 파싱.
    const deadlineMs = new Date(partDeadlineAt).getTime()
    const nowMs = Date.now()
    return Math.max(0, Math.ceil((deadlineMs - nowMs) / 1000))
  }, [partDeadlineAt, timeLimitSeconds])

  const [remainingSeconds, setRemainingSeconds] = useState(computeRemaining)

  // partDeadlineAt이 바뀌면 즉시 동기 재계산 — 다음 tick(최대 TICK_INTERVAL_MS)을
  // 기다리지 않고 라운드 전환 즉시 정확한 남은 시간을 표시한다.
  // https://react.dev/reference/react/useState#storing-information-from-previous-renders
  const [previousDeadline, setPreviousDeadline] = useState(partDeadlineAt)
  if (previousDeadline !== partDeadlineAt) {
    setPreviousDeadline(partDeadlineAt)
    setRemainingSeconds(computeRemaining())
  }

  useEffect(() => {
    if (roomStatus !== 'PLAYING') return

    const intervalId = setInterval(() => {
      setRemainingSeconds(computeRemaining())
    }, TICK_INTERVAL_MS)

    return () => clearInterval(intervalId)
  }, [roomStatus, computeRemaining])

  const isExpiring = remainingSeconds <= EXPIRING_THRESHOLD_SECONDS
  const minutes = Math.floor(remainingSeconds / 60)
  const seconds = remainingSeconds % 60
  const formattedTime = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`

  return { remainingSeconds, isExpiring, formattedTime }
}
