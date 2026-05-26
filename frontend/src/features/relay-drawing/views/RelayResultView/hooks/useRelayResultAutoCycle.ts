'use client'

import { useEffect } from 'react'

import { RELAY_RESULT_AUTO_ADVANCE_MS } from '../../../constants'

interface UseRelayResultAutoCycleParams {
  resultCount: number
  activeResultIndex: number
  setActiveResultIndex: (index: number) => void
}

// 결과 화면에서 RELAY_RESULT_AUTO_ADVANCE_MS 간격으로 다음 캔버스로 자동 전환한다.
// 마지막 캔버스에서는 첫 캔버스로 순환해 슬라이드쇼처럼 계속 돈다.
// 캔버스가 1개 이하이면 자동 전환을 비활성화한다.
//
// activeResultIndex가 바뀔 때마다(자동/수동 모두) effect가 재실행되어 타이머가
// 리셋되므로, 사용자가 우측 패널에서 직접 다른 캔버스를 선택해도 그 시점부터
// 다시 7초 카운트가 시작된다.
export function useRelayResultAutoCycle({
  resultCount,
  activeResultIndex,
  setActiveResultIndex,
}: UseRelayResultAutoCycleParams) {
  useEffect(() => {
    if (resultCount <= 1) return
    const timer = window.setTimeout(() => {
      setActiveResultIndex((activeResultIndex + 1) % resultCount)
    }, RELAY_RESULT_AUTO_ADVANCE_MS)
    return () => window.clearTimeout(timer)
  }, [resultCount, activeResultIndex, setActiveResultIndex])
}
