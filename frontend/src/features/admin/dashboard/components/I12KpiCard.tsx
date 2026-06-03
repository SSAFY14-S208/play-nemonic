'use client'

import { CHART_STATUS_COLORS } from '..'
import type { AnalyticsKpiState, I12KpiData } from '../types'

import { AnalyticsKpiCard } from './AnalyticsKpiCard'

type Props = {
  state: AnalyticsKpiState<I12KpiData>
  onRetry: () => void
}

// I12 — 결과 화면 체류 시간 분포 (4 KPI).
// 신호등 4단계: 빨강 < 5초 / 노랑 5-30초 / 연두 30-60초 / 진녹 60초+.

export function I12KpiCard({ state, onRetry }: Props) {
  const tiles = [
    {
      label: '즉시 이탈 (< 5초)',
      value: state.data?.bounceUnder5s ?? 0,
      accentColor: CHART_STATUS_COLORS.danger,
    },
    {
      label: '짧음 (5-30초)',
      value: state.data?.short5to30s ?? 0,
      accentColor: CHART_STATUS_COLORS.neutral,
    },
    {
      label: '보통 (30-60초)',
      value: state.data?.normal30to60s ?? 0,
      accentColor: CHART_STATUS_COLORS.good,
    },
    {
      label: '몰입 (60초+)',
      value: state.data?.immersedOver60s ?? 0,
      accentColor: CHART_STATUS_COLORS.great,
    },
  ]
  return (
    <AnalyticsKpiCard
      tiles={tiles}
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      onRetry={onRetry}
      columns={4}
    />
  )
}
