'use client'

import { CHART_STATUS_COLORS } from '..'
import type { AnalyticsKpiState, I11KpiData } from '../types'

import { AnalyticsKpiCard } from './AnalyticsKpiCard'

type Props = {
  state: AnalyticsKpiState<I11KpiData>
  onRetry: () => void
}

// I11 — 방문자 완주율 (2 KPI + 비율).
//
// BE distinct-count endpoint(cardinality(uuid))로 distinct 방문자/완주 방문자 측정.
// ES cardinality는 precisionThreshold 안에서 정확도 보장, 그 이상은 통계적 근사치.

export function I11KpiCard({ state, onRetry }: Props) {
  const rate = state.data?.completionRate ?? 0
  const tiles = [
    {
      label: '방문자',
      value: state.data?.visitors ?? 0,
      accentColor: CHART_STATUS_COLORS.accent,
      hint: 'distinct uuid',
    },
    {
      label: '완주 방문자',
      value: state.data?.completedVisitors ?? 0,
      accentColor: CHART_STATUS_COLORS.great,
      hint: 'distinct uuid · funnel_goal_reached',
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
