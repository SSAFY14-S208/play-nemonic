'use client'

import { useCallback, useState } from 'react'

import type { AnalyticsDrillDownState } from '../types'

// 드릴다운 패널 상태 — 차트 요소 클릭 시 set, 닫을 때 null.
//
// 이 PR에선 KPI viz만 실데이터라 차트에서 트리거할 일이 없음. 개발 환경에서만
// 노출되는 mock 버튼이 set 호출 → 패널 슬라이드 인 / 닫힘 / 내용 교체 인터랙션
// 검증용. 실제 차트(I2, I3, I5/I10, I6/I7/I8, I9/I13)는 후속 PR에서 viz 채울
// 때 onDrillDown(filter)을 prop으로 받아 setDrillDown 호출.

export function useAnalyticsDrillDown() {
  const [state, setState] = useState<AnalyticsDrillDownState | null>(null)

  const open = useCallback((next: AnalyticsDrillDownState) => {
    setState(next)
  }, [])

  const close = useCallback(() => {
    setState(null)
  }, [])

  return { state, open, close }
}

export type UseAnalyticsDrillDownReturn = ReturnType<typeof useAnalyticsDrillDown>
