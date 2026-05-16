'use client'

import { CHART_STATUS_COLORS } from '../constants'
import type { AnalyticsKpiState, I11KpiData } from '../types'

import { AnalyticsKpiCard } from './AnalyticsKpiCard'

type Props = {
  state: AnalyticsKpiState<I11KpiData>
  onRetry: () => void
}

// I11 — 방문자 완주율 (2 KPI + 비율).
//
// OSD 원본은 cardinality(uuid)로 distinct 방문자 수를 측정하지만 현 백엔드 API는 total
// hit count만 노출. 백엔드 확장(cardinality 전용 endpoint)이 머지되면 정확값으로 교체.
// 그 전까진 세션 근사치로 표시 + 카드에 "근사치" hint 명시.

export function I11KpiCard({ state, onRetry }: Props) {
  const rate = state.data?.completionRate ?? 0
  const tiles = [
    {
      label: '방문자 (세션 근사)',
      value: state.data?.visitors ?? 0,
      accentColor: CHART_STATUS_COLORS.accent,
      hint: 'distinct uuid은 백엔드 확장 후',
    },
    {
      label: '완주 방문자',
      value: state.data?.completedVisitors ?? 0,
      accentColor: CHART_STATUS_COLORS.great,
      hint: 'event_name:funnel_goal_reached',
    },
    {
      label: '완주율',
      value: `${rate.toFixed(1)}%`,
      accentColor:
        rate >= 50
          ? CHART_STATUS_COLORS.great
          : rate >= 20
            ? CHART_STATUS_COLORS.neutral
            : CHART_STATUS_COLORS.danger,
      hint: '완주 / 방문자',
    },
  ]
  return (
    <AnalyticsKpiCard
      tiles={tiles}
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      onRetry={onRetry}
      columns={3}
    />
  )
}
