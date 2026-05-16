'use client'

import { useMemo } from 'react'

import { runtime } from '@/shared/config'

import {
  AnalyticsFilterBar,
  AnalyticsSection,
  DrillDownPanel,
  I11KpiCard,
  I12KpiCard,
  I1KpiCard,
  PendingVizCard,
  VizCard,
} from './components'
import { SECTION_META, VIZ_META } from './constants'
import {
  useAnalyticsAutoRefresh,
  useAnalyticsDrillDown,
  useAnalyticsFilters,
  useI11Kpi,
  useI12Kpi,
  useI1Kpi,
} from './hooks'
import type { VizId, VizSection } from './types'

// 백오피스 분석 페이지 — OSD iframe 대체본.
//
// 현 PR 범위:
//   - 페이지 골격 + 필터 바 + 30초 자동 갱신.
//   - I1·I11·I12 KPI 시리즈 실데이터 (search size:0 병렬).
//   - 나머지 10개 viz는 PendingVizCard placeholder.
//   - 드릴다운 패널 인프라 + 개발용 mock 트리거.
//
// 백엔드 확장(field-summary 화이트리스트, histogram byField, terms+avg, composite)이
// 머지되면 후속 PR에서 placeholder를 실제 recharts 차트로 교체.

const SECTION_GRID: Record<VizSection, { span: 4 | 6 | 8 | 12; minHeight: number }> = {
  overview: { span: 6, minHeight: 200 },
  channel: { span: 6, minHeight: 260 },
  content: { span: 6, minHeight: 280 },
  flow: { span: 12, minHeight: 320 },
  retention: { span: 6, minHeight: 240 },
}

export default function AdminAnalyticsPage() {
  const filters = useAnalyticsFilters()
  const drillDown = useAnalyticsDrillDown()

  useAnalyticsAutoRefresh(filters.state.autoRefresh, filters.refresh)

  const kpiArgs = useMemo(
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

  const i1State = useI1Kpi(kpiArgs)
  const i11State = useI11Kpi(kpiArgs)
  const i12State = useI12Kpi(kpiArgs)

  const renderViz = (vizId: VizId) => {
    const meta = VIZ_META.find((entry) => entry.id === vizId)
    if (!meta) return null
    if (vizId === 'I1') {
      return <I1KpiCard state={i1State} onRetry={filters.refresh} />
    }
    if (vizId === 'I11') {
      return <I11KpiCard state={i11State} onRetry={filters.refresh} />
    }
    if (vizId === 'I12') {
      return <I12KpiCard state={i12State} onRetry={filters.refresh} />
    }
    return <PendingVizCard title={meta.title} subtitle={meta.subtitle} />
  }

  // mock 드릴다운 트리거 — 개발 환경에서만 노출.
  // 패널 슬라이드 인/아웃 + 내용 교체 인터랙션 검증용. 실제 viz가 채워지면 차트
  // 클릭으로 trigger되므로 이 버튼은 후속 PR에서 제거.
  const handleMockDrillDown = () => {
    drillDown.open({
      vizId: 'I1',
      chartLabel: '진입 (mock 드릴다운)',
      dimensionFilters: [],
      extraQuery: 'event_name:funnel_started',
      description: '실제 차트가 채워지면 막대 클릭으로 trigger됩니다.',
    })
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
        {runtime.isDev && (
          <div className="flex items-center justify-end">
            <button
              type="button"
              onClick={handleMockDrillDown}
              className="caption-b rounded-[var(--radius-md)] border border-dashed border-border-default bg-surface-default px-3 py-1.5 text-fg-secondary transition-colors hover:bg-surface-default"
            >
              dev: 드릴다운 패널 mock 열기
            </button>
          </div>
        )}

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
