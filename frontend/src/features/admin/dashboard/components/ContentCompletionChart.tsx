'use client'

import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis, } from 'recharts'
import type { Formatter } from 'recharts/types/component/DefaultTooltipContent'

import { CHART_FUNNEL_COLORS, CHART_STATUS_COLORS } from '..'
import type { AnalyticsDrillDownState, AnalyticsKpiState } from '../types'
import type { I2Bucket } from '../hooks'

import { ChartFrame } from './ChartFrame'

type Props = {
  state: AnalyticsKpiState<I2Bucket[]>
  onRetry: () => void
  onDrillDown: (next: AnalyticsDrillDownState) => void
}

const FUNNEL_LABEL: Record<string, string> = {
  relay_room_creation: '릴레이드로잉',
  flipbook_room_creation: '플립북',
  community_memo_posting: '커뮤니티 메모',
  fortune_creation: '오늘의 운세',
  gallery_save_share: '갤러리·공유',
  infinite_canvas_creation: '무한 캔버스',
}

const colorFor = (funnelKey: string): string => {
  const palette = CHART_FUNNEL_COLORS as Record<string, string>
  return palette[funnelKey] ?? CHART_STATUS_COLORS.muted
}

const labelFor = (funnelKey: string): string => FUNNEL_LABEL[funnelKey] ?? funnelKey

export function ContentCompletionChart({ state, onRetry, onDrillDown }: Props) {
  const data = (state.data ?? [])
    .filter((bucket) => bucket.started > 0)
    .map((bucket) => ({
      raw: bucket.value,
      label: labelFor(bucket.value),
      rate: bucket.started > 0 ? (bucket.completed / bucket.started) * 100 : 0,
      started: bucket.started,
      completed: bucket.completed,
    }))
    .sort((a, b) => b.rate - a.rate)

  const isEmpty = data.length === 0

  return (
    <ChartFrame
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      isEmpty={isEmpty}
      onRetry={onRetry}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={data}
          layout="vertical"
          margin={{ top: 8, right: 24, bottom: 24, left: 80 }}
        >
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border-default)" />
          <XAxis
            type="number"
            domain={[0, 100]}
            tickFormatter={(value: number) => `${value}%`}
            stroke="var(--color-fg-secondary)"
            tick={{ fontSize: 11 }}
          />
          <YAxis
            type="category"
            dataKey="label"
            width={80}
            stroke="var(--color-fg-secondary)"
            tick={{ fontSize: 12 }}
          />
          <Tooltip
            formatter={
              ((value, name, item) => {
                if (name === 'rate') {
                  const payload = item?.payload as
                    | { started?: number; completed?: number }
                    | undefined
                  const rate = typeof value === 'number' ? value : Number(value ?? 0)
                  return [
                    `${rate.toFixed(1)}% (${payload?.completed ?? 0} / ${payload?.started ?? 0})`,
                    '완주율',
                  ]
                }
                return [String(value ?? ''), name ?? '']
              }) satisfies Formatter
            }
          />
          <Bar
            dataKey="rate"
            radius={[0, 4, 4, 0]}
            cursor="pointer"
            onClick={(entry) => {
              if (!entry || !entry.payload) return
              const raw = entry.payload.raw as string
              onDrillDown({
                vizId: 'I2',
                chartLabel: `완주율 분석: ${labelFor(raw)}`,
                dimensionFilters: [
                  { field: 'metadata.funnel_name', value: raw, negate: false },
                ],
                extraQuery:
                  'event_name:(funnel_started OR funnel_step_completed OR funnel_goal_reached OR funnel_abandoned)',
              })
            }}
          >
            {data.map((entry) => (
              <Cell key={entry.raw} fill={colorFor(entry.raw)} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
}
