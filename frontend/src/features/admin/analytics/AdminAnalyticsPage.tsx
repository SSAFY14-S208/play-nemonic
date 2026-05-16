'use client'

import { useMemo } from 'react'

import {
  AnalyticsSection,
  MetricsFilterBar,
  RowContentActivity,
  RowHostResources,
  RowRequestResponse,
  RowServiceStatus,
  RowUserActivity,
} from './components'
import { ROW_META } from './constants'
import {
  useMetricsAutoRefresh,
  useMetricsFilters,
  type MetricsVizArgs,
} from './hooks'

// 백오피스 통계 및 분석 페이지 — Grafana iframe 대체본.
//
// 23 panel을 5 row 섹션으로 묶어 직접 렌더. 데이터 소스 hybrid:
//   - `/admin/metrics/*` (Prometheus 프록시): 서비스 up, HTTP, host 리소스, WS gauge
//   - `/admin/logs/*` (OSD): 활성 uuid/session, funnel/entry timeline, top event
// 15초 주기 자동 갱신, 페이지 가시성 hidden 시 호출 스킵.

export default function AdminAnalyticsPage() {
  const filters = useMetricsFilters()
  useMetricsAutoRefresh(filters.state.autoRefresh, filters.refresh)

  const args = useMemo<MetricsVizArgs>(
    () => ({
      timeRange: filters.timeRange,
      preset: filters.state.preset,
      refreshNonce: filters.state.refreshNonce,
    }),
    [filters.timeRange, filters.state.preset, filters.state.refreshNonce],
  )

  const renderRow = (rowKey: (typeof ROW_META)[number]['key']) => {
    switch (rowKey) {
      case 'service-status':
        return <RowServiceStatus args={args} />
      case 'request-response':
        return <RowRequestResponse args={args} onRetry={filters.refresh} />
      case 'user-activity':
        return <RowUserActivity args={args} onRetry={filters.refresh} />
      case 'content-activity':
        return <RowContentActivity args={args} onRetry={filters.refresh} />
      case 'host-resources':
        return <RowHostResources args={args} onRetry={filters.refresh} />
      default:
        return null
    }
  }

  return (
    <div className="-mx-8 -my-6 flex h-[calc(100%+3rem)] flex-col">
      <MetricsFilterBar
        state={filters.state}
        onPresetChange={filters.setPreset}
        onCustomRangeChange={filters.setCustomRange}
        onAutoRefreshChange={filters.setAutoRefresh}
        onRefresh={filters.refresh}
      />
      <div className="flex flex-1 flex-col gap-8 overflow-y-auto bg-surface-subtle px-6 py-6">
        {ROW_META.map((row) => (
          <AnalyticsSection key={row.key} title={row.title}>
            {renderRow(row.key)}
          </AnalyticsSection>
        ))}
      </div>
    </div>
  )
}
