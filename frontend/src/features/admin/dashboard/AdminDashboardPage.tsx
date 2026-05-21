'use client'

import { useMemo } from 'react'

import {
  AbandonElapsedCards,
  AnalyticsFilterBar,
  AnalyticsSection,
  ContentCompletionChart,
  DrillDownPanel,
  DwellTimeBar,
  EntryChannelDonut,
  EntryTimelineChart,
  FunnelAbandonChart,
  I11KpiCard,
  I12KpiCard,
  I1KpiCard,
  PendingVizCard,
  PhoneOfficialStoreTimelineChart,
  ShareRateDonut,
  VizCard,
} from './components'
import { SECTION_META, VIZ_META } from './constants'
import {
  useAnalyticsAutoRefresh,
  useAnalyticsDrillDown,
  useAnalyticsFilters,
  useI10EntryChannelTimeline,
  useI11Kpi,
  useI12Kpi,
  useI13AbandonElapsed,
  useI14PhoneOfficialStoreClicks,
  useI1Kpi,
  useI2ContentCompletion,
  useI3FunnelAbandon,
  useI5EntryTimeline,
  useI6ShareRate,
  useI7EntryChannel,
  useI9DwellTime,
} from './hooks'
import type { VizId, VizSection } from './types'

// 백오피스 대시보드 페이지 — Grafana iframe 대체본.
//
// 12개 viz 모두 BE 로그 집계 API 직접 호출로 채워짐.
//   - KPI 카드 3종 (I1·I11·I12) — search/distinct-count
//   - donut 2종 (I6·I7) — field-summary / terms-with-subs
//     (I7은 SNS별 카테고리까지 흡수해 단일 도넛으로 통합)
//   - 가로 막대 2종 (I2·I9) — terms-with-subs / terms-with-metric
//   - stacked area 2종 (I5·I10) — histogram byField
//   - line 1종 (I14) — histogram(phone_official_store_clicked)
//   - 단계 카드 1종 (I3) — composite-buckets
//   - 3-phase 막대 1종 (I13) — filtered-metrics
//
// 모든 차트는 클릭 시 우측 드릴다운 패널을 열어 해당 dimension의 raw 이벤트 50건 +
// 시계열을 표시한다.

const SECTION_GRID: Record<VizSection, { span: 4 | 6 | 8 | 12; minHeight: number }> = {
  overview: { span: 6, minHeight: 200 },
  channel: { span: 6, minHeight: 280 },
  content: { span: 6, minHeight: 300 },
  flow: { span: 12, minHeight: 360 },
  retention: { span: 6, minHeight: 260 },
}

export default function AdminDashboardPage() {
  const filters = useAnalyticsFilters()
  const drillDown = useAnalyticsDrillDown()

  useAnalyticsAutoRefresh(filters.state.autoRefresh, filters.refresh)

  const vizArgs = useMemo(
    () => ({
      timeRange: filters.timeRange,
      serviceFilters: filters.serviceFilters,
      serviceQuery: filters.serviceQuery,
      refreshNonce: filters.state.refreshNonce,
    }),
    [
      filters.timeRange,
      filters.serviceFilters,
      filters.serviceQuery,
      filters.state.refreshNonce,
    ],
  )

  // KPI
  const i1State = useI1Kpi(vizArgs)
  const i11State = useI11Kpi(vizArgs)
  const i12State = useI12Kpi(vizArgs)
  // 채널
  const i7State = useI7EntryChannel(vizArgs)
  const i10State = useI10EntryChannelTimeline(vizArgs)
  const i14State = useI14PhoneOfficialStoreClicks(vizArgs)
  const i6State = useI6ShareRate(vizArgs)
  // 컨텐츠
  const i2State = useI2ContentCompletion(vizArgs)
  const i5State = useI5EntryTimeline(vizArgs)
  // 흐름
  const i3State = useI3FunnelAbandon(vizArgs)
  // 체류
  const i9State = useI9DwellTime(vizArgs)
  const i13State = useI13AbandonElapsed(vizArgs)

  const renderViz = (vizId: VizId) => {
    const meta = VIZ_META.find((entry) => entry.id === vizId)
    if (!meta) return null
    switch (vizId) {
      case 'I1':
        return <I1KpiCard state={i1State} onRetry={filters.refresh} />
      case 'I11':
        return <I11KpiCard state={i11State} onRetry={filters.refresh} />
      case 'I12':
        return <I12KpiCard state={i12State} onRetry={filters.refresh} />
      case 'I7':
        return (
          <EntryChannelDonut
            state={i7State}
            onRetry={filters.refresh}
            onDrillDown={drillDown.open}
          />
        )
      case 'I10':
        return (
          <EntryTimelineChart
            vizId="I10"
            state={i10State}
            onRetry={filters.refresh}
            onDrillDown={drillDown.open}
            colorMode="entry"
            dimensionField="metadata.entry_type"
            baseEventQuery="event_name:landing_source_detected"
          />
        )
      case 'I14':
        return (
          <PhoneOfficialStoreTimelineChart
            state={i14State}
            onRetry={filters.refresh}
            onDrillDown={drillDown.open}
          />
        )
      case 'I6':
        return (
          <ShareRateDonut
            state={i6State}
            onRetry={filters.refresh}
            onDrillDown={drillDown.open}
          />
        )
      case 'I2':
        return (
          <ContentCompletionChart
            state={i2State}
            onRetry={filters.refresh}
            onDrillDown={drillDown.open}
          />
        )
      case 'I5':
        return (
          <EntryTimelineChart
            vizId="I5"
            state={i5State}
            onRetry={filters.refresh}
            onDrillDown={drillDown.open}
            colorMode="funnel"
            dimensionField="metadata.funnel_name"
            baseEventQuery="event_name:funnel_started"
          />
        )
      case 'I3':
        return (
          <FunnelAbandonChart
            state={i3State}
            onRetry={filters.refresh}
            onDrillDown={drillDown.open}
          />
        )
      case 'I9':
        return (
          <DwellTimeBar
            state={i9State}
            onRetry={filters.refresh}
            onDrillDown={drillDown.open}
          />
        )
      case 'I13':
        return (
          <AbandonElapsedCards
            state={i13State}
            onRetry={filters.refresh}
            onDrillDown={drillDown.open}
          />
        )
      default:
        return <PendingVizCard title={meta.title} subtitle={meta.subtitle} />
    }
  }

  return (
    <div className="-mx-8 -my-6 flex h-[calc(100%+3rem)] flex-col">
      <AnalyticsFilterBar
        state={filters.state}
        onPresetChange={filters.setPreset}
        onCustomRangeChange={filters.setCustomRange}
        onServiceToggle={filters.toggleService}
        onAutoRefreshChange={filters.setAutoRefresh}
        onRefresh={filters.refresh}
      />

      <div className="flex flex-1 flex-col gap-8 overflow-y-auto bg-surface-subtle px-6 py-6">
        {SECTION_META.map((section) => (
          <AnalyticsSection key={section.key} title={section.title}>
            {VIZ_META.filter((viz) => viz.section === section.key).map((viz) => {
              const grid = SECTION_GRID[section.key as VizSection]
              return (
                <VizCard
                  key={viz.id}
                  vizId={viz.id}
                  title={viz.title}
                  subtitle={viz.subtitle}
                  span={grid.span}
                  minHeight={grid.minHeight}
                >
                  {renderViz(viz.id)}
                </VizCard>
              )
            })}
          </AnalyticsSection>
        ))}
      </div>

      <DrillDownPanel
        state={drillDown.state}
        timeRange={filters.timeRange}
        serviceQuery={filters.serviceQuery}
        onClose={drillDown.close}
      />
    </div>
  )
}
