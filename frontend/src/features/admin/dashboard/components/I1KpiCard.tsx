'use client'

import { CHART_STATUS_COLORS } from '..'
import type { AnalyticsKpiState, I1KpiData } from '../types'

import { AnalyticsKpiCard } from './AnalyticsKpiCard'

type Props = {
  state: AnalyticsKpiState<I1KpiData>
  onRetry: () => void
}

// I1 — 오늘의 핵심 지표 (4 KPI).
// 의미 색: 활성=중립(slate), 진입=brand violet, 완료=success green, 이탈=danger rose.

export function I1KpiCard({ state, onRetry }: Props) {
  const tiles = [
    {
      label: '활성 세션',
      value: state.data?.activeSessions ?? 0,
      accentColor: CHART_STATUS_COLORS.muted,
      hint: 'client_alive heartbeat',
    },
    {
      label: '진입',
      value: state.data?.funnelStarted ?? 0,
      accentColor: CHART_STATUS_COLORS.accent,
      hint: 'funnel_started',
    },
    {
      label: '완료',
      value: state.data?.funnelGoalReached ?? 0,
      accentColor: CHART_STATUS_COLORS.great,
      hint: 'funnel_goal_reached',
    },
    {
      label: '이탈',
      value: state.data?.funnelAbandoned ?? 0,
      accentColor: CHART_STATUS_COLORS.danger,
      hint: 'funnel/room/creation/result abandoned',
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
