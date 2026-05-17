'use client'

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import type { Formatter } from 'recharts/types/component/DefaultTooltipContent'

import { CHART_STATUS_COLORS } from '../constants'
import type { AnalyticsDrillDownState, AnalyticsKpiState } from '../types'
import type { I8Bucket } from '../hooks/useAnalyticsCharts'

import { ChartFrame } from './ChartFrame'

type Props = {
  state: AnalyticsKpiState<I8Bucket[]>
  onRetry: () => void
  onDrillDown: (next: AnalyticsDrillDownState) => void
}

// referrer raw host → SNS 그룹 라벨. brand color는 OSD 마케팅 대시보드와 일치.
const SNS_BUCKETS: Array<{ label: string; color: string; match: (host: string) => boolean }> = [
  { label: '인스타그램', color: 'hsl(330 81% 50%)', match: (host) => host.includes('instagram') },
  {
    label: '트위터',
    color: 'hsl(203 89% 53%)',
    match: (host) => host.includes('twitter') || host.includes('x.com') || host.includes('t.co'),
  },
  { label: '카카오톡', color: 'hsl(53 100% 50%)', match: (host) => host.includes('kakao') },
  { label: '페이스북', color: 'hsl(221 44% 41%)', match: (host) => host.includes('facebook') },
  { label: '링크드인', color: 'hsl(201 100% 35%)', match: (host) => host.includes('linkedin') },
]

const categorize = (
  buckets: I8Bucket[],
): Array<{ label: string; color: string; value: number; rawHosts: string[] }> => {
  const accum = new Map<string, { color: string; value: number; rawHosts: string[] }>()
  for (const bucket of buckets) {
    const matched = SNS_BUCKETS.find((entry) => entry.match(bucket.value.toLowerCase()))
    const label = matched?.label ?? '기타'
    const color = matched?.color ?? CHART_STATUS_COLORS.muted
    const existing = accum.get(label) ?? { color, value: 0, rawHosts: [] }
    existing.value += bucket.count
    existing.rawHosts.push(bucket.value)
    accum.set(label, existing)
  }
  return Array.from(accum.entries()).map(([label, value]) => ({ label, ...value }))
}

export function SnsEntryDonut({ state, onRetry, onDrillDown }: Props) {
  const data = categorize(state.data ?? [])
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
            nameKey="label"
            innerRadius="55%"
            outerRadius="85%"
            paddingAngle={2}
            stroke="var(--color-surface-default)"
            strokeWidth={2}
            onClick={(entry) => {
              if (!entry || !entry.payload) return
              const rawHosts = entry.payload.rawHosts as string[]
              const label = entry.payload.label as string
              onDrillDown({
                vizId: 'I8',
                chartLabel: `SNS 유입: ${label}`,
                dimensionFilters: [],
                extraQuery:
                  'event_name:landing_source_detected AND metadata.entry_type:social' +
                  (rawHosts.length > 0
                    ? ` AND metadata.referrer:(${rawHosts.map((host) => `"${host}"`).join(' OR ')})`
                    : ''),
              })
            }}
          >
            {data.map((entry) => (
              <Cell key={entry.label} fill={entry.color} cursor="pointer" />
            ))}
          </Pie>
          <Tooltip
            formatter={
              ((value, name) => {
                const count = typeof value === 'number' ? value : Number(value ?? 0)
                return [`${count.toLocaleString('ko-KR')}건`, name ?? '']
              }) satisfies Formatter
            }
          />
        </PieChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
}
