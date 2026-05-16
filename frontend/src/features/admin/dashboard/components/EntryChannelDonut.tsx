'use client'

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import type { Formatter } from 'recharts/types/component/DefaultTooltipContent'

import { CHART_ENTRY_COLORS, CHART_STATUS_COLORS } from '../constants'
import type { AnalyticsKpiState, AnalyticsDrillDownState } from '../types'
import type { I7Bucket } from '../hooks/useAnalyticsCharts'

import { ChartFrame } from './ChartFrame'

type Props = {
  state: AnalyticsKpiState<I7Bucket[]>
  onRetry: () => void
  onDrillDown: (next: AnalyticsDrillDownState) => void
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

const colorFor = (entryKey: string): string => {
  const palette = CHART_ENTRY_COLORS as Record<string, string>
  return palette[entryKey] ?? CHART_STATUS_COLORS.muted
}

const labelFor = (entryKey: string): string => ENTRY_LABEL[entryKey] ?? entryKey

const tooltipFormatter: Formatter = (value, name) => {
  const count = typeof value === 'number' ? value : Number(value ?? 0)
  return [`${count.toLocaleString('ko-KR')}건`, name ?? '']
}

export function EntryChannelDonut({ state, onRetry, onDrillDown }: Props) {
  const data = (state.data ?? []).map((bucket) => ({
    name: labelFor(bucket.value),
    raw: bucket.value,
    value: bucket.count,
  }))
  const isEmpty = data.length === 0

  return (
    <ChartFrame
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      isEmpty={isEmpty}
      onRetry={onRetry}
    >
      <ResponsiveContainer width="100%" height="100%">
        <PieChart margin={{ top: 12, right: 12, bottom: 12, left: 12 }}>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            innerRadius="55%"
            outerRadius="85%"
            paddingAngle={2}
            stroke="var(--color-surface-default)"
            strokeWidth={2}
            onClick={(entry) => {
              if (!entry || !entry.payload) return
              const raw = entry.payload.raw as string
              onDrillDown({
                vizId: 'I7',
                chartLabel: `유입 경로: ${labelFor(raw)}`,
                dimensionFilters: [
                  { field: 'metadata.entry_type', value: raw, negate: false },
                ],
                extraQuery: 'event_name:landing_source_detected',
              })
            }}
          >
            {data.map((entry) => (
              <Cell key={entry.raw} fill={colorFor(entry.raw)} cursor="pointer" />
            ))}
          </Pie>
          <Tooltip formatter={tooltipFormatter} />
        </PieChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
}
