'use client'

import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { Formatter } from 'recharts/types/component/DefaultTooltipContent'

import { METRICS_COLORS } from '..'
import type { TimelineRow } from '../hooks'

type Props = {
  rows: TimelineRow[]
  // 각 row에서 series로 쓸 키 목록.
  seriesKeys: string[]
  // series 키 → 표시 라벨 변환 (예: '0.5' → 'p50').
  labelFor?: (seriesKey: string) => string
  // series 키 → 색.
  colorFor?: (seriesKey: string) => string
  // y 값 포맷 (예: '5.2%', '12.3 req/s').
  valueFormatter?: (value: number) => string
  // y 축 도메인. 비율 (0-1) / 퍼센트 (0-100) 등에 명시적 지정.
  yDomain?: [number | 'auto', number | 'auto']
  // legend 표시 여부 (단일 series면 끄기).
  showLegend?: boolean
  // 시계열 점 끊김 처리 — null 값을 segment break로 둘지.
  connectNulls?: boolean
}

const formatTs = (ts: string): string => {
  const date = new Date(ts)
  if (Number.isNaN(date.getTime())) return ts
  return date.toLocaleString('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const DEFAULT_COLORS = [
  METRICS_COLORS.accent,
  METRICS_COLORS.secondary,
  METRICS_COLORS.tertiary,
  METRICS_COLORS.great,
  METRICS_COLORS.warn,
  METRICS_COLORS.neutral,
  METRICS_COLORS.danger,
]

export function MetricsLineChart({
  rows,
  seriesKeys,
  labelFor,
  colorFor,
  valueFormatter,
  yDomain,
  showLegend,
  connectNulls = false,
}: Props) {
  const legendOn = showLegend ?? seriesKeys.length > 1
  const tooltipFormatter: Formatter = (value, name) => {
    const numeric = typeof value === 'number' ? value : Number(value ?? 0)
    const formatted = valueFormatter
      ? valueFormatter(numeric)
      : numeric.toLocaleString('ko-KR', { maximumFractionDigits: 3 })
    const key = String(name ?? '')
    const labeled = labelFor ? labelFor(key) : key
    return [formatted, labeled]
  }

  const yTickFormatter = valueFormatter
    ? (value: number) => valueFormatter(value)
    : undefined

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={rows} margin={{ top: 12, right: 24, bottom: 24, left: 12 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border-default)" />
        <XAxis
          dataKey="ts"
          stroke="var(--color-fg-secondary)"
          tick={{ fontSize: 10 }}
          tickFormatter={formatTs}
          minTickGap={40}
        />
        <YAxis
          stroke="var(--color-fg-secondary)"
          tick={{ fontSize: 10 }}
          tickFormatter={yTickFormatter}
          domain={yDomain}
        />
        <Tooltip
          labelFormatter={(value) => formatTs(String(value))}
          formatter={tooltipFormatter}
        />
        {legendOn && (
          <Legend
            formatter={(value: string) => (labelFor ? labelFor(value) : value)}
            wrapperStyle={{ fontSize: 11 }}
          />
        )}
        {seriesKeys.map((key, index) => (
          <Line
            key={key}
            type="monotone"
            dataKey={key}
            stroke={
              colorFor
                ? colorFor(key)
                : DEFAULT_COLORS[index % DEFAULT_COLORS.length]
            }
            strokeWidth={2}
            dot={false}
            connectNulls={connectNulls}
            isAnimationActive={false}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  )
}
