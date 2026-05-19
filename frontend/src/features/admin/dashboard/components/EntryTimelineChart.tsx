'use client'

import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { Formatter } from 'recharts/types/component/DefaultTooltipContent'

import { formatKoreanDateTime } from '@/shared/utils'
import {
  CHART_ENTRY_COLORS,
  CHART_FUNNEL_COLORS,
  CHART_STATUS_COLORS,
} from '../constants'
import type { AnalyticsDrillDownState, AnalyticsKpiState, VizId } from '../types'
import type { TimelineData } from '../hooks/useAnalyticsCharts'

import { ChartFrame } from './ChartFrame'

type ColorMode = 'funnel' | 'entry'

type Props = {
  vizId: Extract<VizId, 'I5' | 'I10'>
  state: AnalyticsKpiState<TimelineData>
  onRetry: () => void
  onDrillDown: (next: AnalyticsDrillDownState) => void
  colorMode: ColorMode
  // 차트 드릴다운 시 dimension 필드(metadata.funnel_name | metadata.entry_type).
  dimensionField: string
  // 드릴다운 query에 추가할 event_name 조건.
  baseEventQuery: string
}

const FUNNEL_LABEL: Record<string, string> = {
  relay_room_creation: '릴레이드로잉',
  flipbook_room_creation: '플립북',
  community_memo_posting: '커뮤니티 메모',
  fortune_creation: '오늘의 운세',
  gallery_save_share: '갤러리·공유',
  infinite_canvas_creation: '무한 캔버스',
}

const ENTRY_LABEL: Record<string, string> = {
  direct: '직접 접속',
  search: '검색',
  social: 'SNS',
  qr: 'QR 코드',
  share: '공유 링크',
  campaign: '캠페인',
  unknown: '알 수 없음',
}

const palette = (mode: ColorMode): Record<string, string> =>
  mode === 'funnel'
    ? (CHART_FUNNEL_COLORS as Record<string, string>)
    : (CHART_ENTRY_COLORS as Record<string, string>)

const labelMap = (mode: ColorMode): Record<string, string> =>
  mode === 'funnel' ? FUNNEL_LABEL : ENTRY_LABEL

const formatBucketTime = (ts: string): string => {
  return formatKoreanDateTime(ts, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function EntryTimelineChart({
  vizId,
  state,
  onRetry,
  onDrillDown,
  colorMode,
  dimensionField,
  baseEventQuery,
}: Props) {
  const timeline = state.data
  const colors = palette(colorMode)
  const labels = labelMap(colorMode)
  const series = timeline?.seriesKeys ?? []
  const data = (timeline?.buckets ?? []).map((bucket) => {
    const flat: Record<string, number | string> = { ts: bucket.ts }
    for (const key of series) flat[key] = bucket.byField[key] ?? 0
    return flat
  })
  const isEmpty = data.length === 0 || series.length === 0

  return (
    <ChartFrame
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      isEmpty={isEmpty}
      onRetry={onRetry}
    >
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 12, right: 24, bottom: 24, left: 12 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border-default)" />
          <XAxis
            dataKey="ts"
            stroke="var(--color-fg-secondary)"
            tick={{ fontSize: 10 }}
            tickFormatter={formatBucketTime}
            minTickGap={40}
          />
          <YAxis stroke="var(--color-fg-secondary)" tick={{ fontSize: 10 }} />
          <Tooltip
            labelFormatter={(value) => formatBucketTime(String(value))}
            formatter={
              ((value, name) => {
                const count = typeof value === 'number' ? value : Number(value ?? 0)
                const key = String(name ?? '')
                return [`${count.toLocaleString('ko-KR')}건`, labels[key] ?? key]
              }) satisfies Formatter
            }
          />
          <Legend
            formatter={(value: string) => labels[value] ?? value}
            wrapperStyle={{ fontSize: 11 }}
          />
          {series.map((key) => (
            <Area
              key={key}
              type="monotone"
              dataKey={key}
              stackId="1"
              stroke={colors[key] ?? CHART_STATUS_COLORS.muted}
              fill={colors[key] ?? CHART_STATUS_COLORS.muted}
              fillOpacity={0.6}
              activeDot={{
                r: 4,
                cursor: 'pointer',
                onClick: () =>
                  onDrillDown({
                    vizId,
                    chartLabel: `${labels[key] ?? key} 시계열`,
                    dimensionFilters: [
                      { field: dimensionField, value: key, negate: false },
                    ],
                    extraQuery: baseEventQuery,
                  }),
              }}
            />
          ))}
        </AreaChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
}
