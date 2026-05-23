'use client'

import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { Formatter } from 'recharts/types/component/DefaultTooltipContent'

import { formatKoreanDateTime } from '@/shared/utils'
import { CHART_STATUS_COLORS } from '..'
import type { AnalyticsDrillDownState, AnalyticsKpiState } from '../types'
import type { I14Result } from '../hooks'

import { ChartFrame } from './ChartFrame'

type Props = {
  state: AnalyticsKpiState<I14Result>
  onRetry: () => void
  onDrillDown: (next: AnalyticsDrillDownState) => void
}

const tooltipFormatter: Formatter = (value) => {
  const count = typeof value === 'number' ? value : Number(value ?? 0)
  return [`${count.toLocaleString('ko-KR')}건`, '클릭']
}

const formatBucketTime = (ts: string): string =>
  formatKoreanDateTime(ts, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })

export function PhoneOfficialStoreTimelineChart({ state, onRetry, onDrillDown }: Props) {
  const buckets = state.data?.buckets ?? []
  const total = state.data?.total ?? 0
  const isEmpty = buckets.length === 0 || buckets.every((bucket) => bucket.count === 0)

  return (
    <ChartFrame
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      isEmpty={isEmpty}
      onRetry={onRetry}
    >
      <div className="flex h-full flex-col gap-2 p-3">
        <div className="caption-r text-fg-secondary">
          기간 누적 {total.toLocaleString('ko-KR')}회
        </div>
        <div className="min-h-0 flex-1">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart
              data={buckets}
              margin={{ top: 8, right: 16, bottom: 24, left: 0 }}
            >
              <CartesianGrid
                strokeDasharray="3 3"
                stroke="var(--color-border-default)"
              />
              <XAxis
                dataKey="ts"
                stroke="var(--color-fg-secondary)"
                tick={{ fontSize: 10 }}
                tickFormatter={formatBucketTime}
                minTickGap={40}
              />
              <YAxis
                stroke="var(--color-fg-secondary)"
                tick={{ fontSize: 10 }}
                allowDecimals={false}
              />
              <Tooltip
                labelFormatter={(value) => formatBucketTime(String(value))}
                formatter={tooltipFormatter}
              />
              <Line
                type="monotone"
                dataKey="count"
                stroke={CHART_STATUS_COLORS.accent}
                strokeWidth={2}
                dot={false}
                activeDot={{
                  r: 4,
                  cursor: 'pointer',
                  onClick: () =>
                    onDrillDown({
                      vizId: 'I14',
                      chartLabel: '핸드폰 → 공식몰 이동',
                      dimensionFilters: [],
                      extraQuery: 'event_name:phone_official_store_clicked',
                    }),
                }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </ChartFrame>
  )
}
