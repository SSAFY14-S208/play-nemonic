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

// SNS 키는 `sns.<host>` 형태 — useI7EntryChannel이 metadata.referrer를 카테고라이즈해 부여한다.
// 색은 각 SNS 브랜드 컬러를 직접 매핑한다(디자인 시스템에 동등 토큰이 없어 chart 전용).
const SNS_BRAND_COLORS: Record<string, string> = {
  'sns.instagram': 'hsl(330 81% 50%)',
  'sns.kakao': 'hsl(53 100% 50%)',
  'sns.twitter': 'hsl(203 89% 53%)',
  'sns.facebook': 'hsl(221 44% 41%)',
  'sns.linkedin': 'hsl(201 100% 35%)',
  'sns.other': CHART_STATUS_COLORS.muted,
}

const ENTRY_LABEL: Record<string, string> = {
  direct: '직접 접속',
  search: '검색',
  qr: 'QR 코드',
  share: '공유 링크',
  unknown: '알 수 없음',
  'sns.instagram': '인스타그램',
  'sns.kakao': '카카오톡',
  'sns.twitter': '트위터',
  'sns.facebook': '페이스북',
  'sns.linkedin': '링크드인',
  'sns.other': '기타 SNS',
}

const colorFor = (entryKey: string): string => {
  if (entryKey.startsWith('sns.')) {
    return SNS_BRAND_COLORS[entryKey] ?? CHART_STATUS_COLORS.muted
  }
  const palette = CHART_ENTRY_COLORS as Record<string, string>
  return palette[entryKey] ?? CHART_STATUS_COLORS.muted
}

const labelFor = (entryKey: string): string => ENTRY_LABEL[entryKey] ?? entryKey

const tooltipFormatter: Formatter = (value, name) => {
  const count = typeof value === 'number' ? value : Number(value ?? 0)
  return [`${count.toLocaleString('ko-KR')}건`, name ?? '']
}

// SNS 펼친 버킷의 drill-down은 referrer host를 같이 좁혀줘야 일반 진입 채널과 의미가 일치.
function buildDrillDown(bucket: I7Bucket): AnalyticsDrillDownState {
  if (bucket.value.startsWith('sns.')) {
    const hosts = bucket.rawHosts ?? []
    const referrerClause =
      hosts.length > 0
        ? ` AND metadata.referrer:(${hosts.map((host) => `"${host}"`).join(' OR ')})`
        : ''
    return {
      vizId: 'I7',
      chartLabel: `유입 경로: ${labelFor(bucket.value)}`,
      dimensionFilters: [
        { field: 'metadata.entry_type', value: 'social', negate: false },
      ],
      extraQuery: `event_name:landing_source_detected${referrerClause}`,
    }
  }
  return {
    vizId: 'I7',
    chartLabel: `유입 경로: ${labelFor(bucket.value)}`,
    dimensionFilters: [
      { field: 'metadata.entry_type', value: bucket.value, negate: false },
    ],
    extraQuery: 'event_name:landing_source_detected',
  }
}

export function EntryChannelDonut({ state, onRetry, onDrillDown }: Props) {
  const data = (state.data ?? []).map((bucket) => ({
    name: labelFor(bucket.value),
    raw: bucket.value,
    rawHosts: bucket.rawHosts,
    value: bucket.count,
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
              onDrillDown(
                buildDrillDown({
                  value: entry.payload.raw as string,
                  count: entry.payload.value as number,
                  rawHosts: entry.payload.rawHosts as string[] | undefined,
                }),
              )
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
