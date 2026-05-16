'use client'

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import type { Formatter } from 'recharts/types/component/DefaultTooltipContent'

import { CHART_FUNNEL_COLORS, CHART_STATUS_COLORS } from '../constants'
import type { AnalyticsDrillDownState, AnalyticsKpiState } from '../types'
import type { I6Bucket } from '../hooks/useAnalyticsCharts'

import { ChartFrame } from './ChartFrame'

type Props = {
  state: AnalyticsKpiState<I6Bucket[]>
  onRetry: () => void
  onDrillDown: (next: AnalyticsDrillDownState) => void
}

const FUNNEL_LABEL: Record<string, string> = {
  relay_room_creation: '릴레이드로잉',
  flipbook_room_creation: '플립북',
  community_memo_posting: '커뮤니티 메모',
  fortune_creation: '오늘의 운세',
  gallery_save_share: '갤러리·공유',
}

const colorFor = (funnelKey: string): string => {
  const palette = CHART_FUNNEL_COLORS as Record<string, string>
  return palette[funnelKey] ?? CHART_STATUS_COLORS.muted
}

const labelFor = (funnelKey: string): string => FUNNEL_LABEL[funnelKey] ?? funnelKey

export function ShareRateDonut({ state, onRetry, onDrillDown }: Props) {
  const data = (state.data ?? []).map((bucket) => ({
    raw: bucket.value,
    name: labelFor(bucket.value),
    value: bucket.shared,
    goal: bucket.goal,
    abandoned: bucket.abandoned,
  }))
  const isEmpty = data.length === 0 || data.every((entry) => entry.value === 0)

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
                vizId: 'I6',
                chartLabel: `공유 도달: ${labelFor(raw)}`,
                dimensionFilters: [
                  { field: 'metadata.funnel_name', value: raw, negate: false },
                ],
                extraQuery: 'event_name:funnel_goal_reached',
                description: '결과 도달 이후 공유까지 도달한 세션의 raw 이벤트',
              })
            }}
          >
            {data.map((entry) => (
              <Cell key={entry.raw} fill={colorFor(entry.raw)} cursor="pointer" />
            ))}
          </Pie>
          <Tooltip
            formatter={
              ((value, name, item) => {
                const payload = item?.payload as
                  | { goal?: number; abandoned?: number }
                  | undefined
                const shared = typeof value === 'number' ? value : Number(value ?? 0)
                const goal = payload?.goal ?? 0
                const abandoned = payload?.abandoned ?? 0
                return [
                  `공유 ${shared.toLocaleString('ko-KR')} / 도달 ${goal.toLocaleString('ko-KR')} (이탈 ${abandoned.toLocaleString('ko-KR')})`,
                  name ?? '',
                ]
              }) satisfies Formatter
            }
          />
        </PieChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
}
