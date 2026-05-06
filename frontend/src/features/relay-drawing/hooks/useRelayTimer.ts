'use client'

import { useCallback, useEffect, useState } from 'react'
import { useRelayDrawingStore } from '../stores'

const EXPIRING_THRESHOLD_SECONDS = 10

interface UseRelayTimerReturn {
  remainingSeconds: number
  isExpiring: boolean
  formattedTime: string
  syncRemainingTime: (seconds: number) => void
}

export function useRelayTimer(): UseRelayTimerReturn {
  const timeLimitSeconds = useRelayDrawingStore((state) => state.timeLimitSeconds)
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey)
  const roomStatus = useRelayDrawingStore((state) => state.roomStatus)
  const completeRound = useRelayDrawingStore((state) => state.completeRound)

  const [remainingSeconds, setRemainingSeconds] = useState(timeLimitSeconds)
  // 라운드/제한시간 변경을 "이전 렌더 정보"로 추적해, 변경이 감지되는 순간
  // 같은 렌더 안에서 즉시 리셋한다. effect로 처리하면 commit 후 추가 setState
  // 가 일어나 cascading re-render(React 19 lint: react-hooks/set-state-in-effect)
  // 가 발생한다.
  // https://react.dev/reference/react/useState#storing-information-from-previous-renders
  const [previousRoundKey, setPreviousRoundKey] = useState(activeRoundKey)
  const [previousTimeLimit, setPreviousTimeLimit] = useState(timeLimitSeconds)

  if (
    previousRoundKey !== activeRoundKey ||
    previousTimeLimit !== timeLimitSeconds
  ) {
    setPreviousRoundKey(activeRoundKey)
    setPreviousTimeLimit(timeLimitSeconds)
    setRemainingSeconds(timeLimitSeconds)
  }

  // 카운트다운 — PLAYING 상태일 때만 매 초 1씩 감소.
  // remainingSeconds가 dep이 아니라서 interval은 라운드 단위로만 재생성된다.
  useEffect(() => {
    if (roomStatus !== 'PLAYING') return

    const intervalId = setInterval(() => {
      setRemainingSeconds((previous) => Math.max(previous - 1, 0))
    }, 1000)

    return () => clearInterval(intervalId)
  }, [roomStatus, activeRoundKey])

  // 0이 되는 시점에 정확히 한 번 completeRound 호출.
  // remainingSeconds가 계속 0이어도 dep이 변하지 않아 추가 호출 없음 —
  // completeRound가 activeRoundKey를 바꾸면 위 if-during-render 분기가 새 값으로
  // 리셋해 다음 라운드의 카운트다운이 시작된다.
  useEffect(() => {
    if (remainingSeconds === 0 && roomStatus === 'PLAYING') {
      completeRound()
    }
  }, [remainingSeconds, roomStatus, completeRound])

  const syncRemainingTime = useCallback((seconds: number) => {
    setRemainingSeconds(seconds)
  }, [])

  const isExpiring = remainingSeconds <= EXPIRING_THRESHOLD_SECONDS
  const minutes = Math.floor(remainingSeconds / 60)
  const seconds = remainingSeconds % 60
  const formattedTime = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`

  return { remainingSeconds, isExpiring, formattedTime, syncRemainingTime }
}
