'use client'

import {
  PolarAngleAxis,
  RadialBar,
  RadialBarChart,
  ResponsiveContainer,
} from 'recharts'

import { METRICS_COLORS } from '../constants'

type Props = {
  // 0~100. clamp 처리됨.
  percent: number
  // 큰 라벨 (예: "62%").
  label?: string
  // 부가 텍스트 (예: "사용 중").
  sublabel?: string
  // 단계 임계값. 기본: <60 great, <80 warn, >=80 danger.
  thresholds?: { warn: number; danger: number }
}

const colorFor = (percent: number, warn: number, danger: number): string => {
  if (percent >= danger) return METRICS_COLORS.danger
  if (percent >= warn) return METRICS_COLORS.warn
  return METRICS_COLORS.great
}

export function MetricsGauge({
  percent,
  label,
  sublabel,
  thresholds = { warn: 60, danger: 80 },
}: Props) {
  const clamped = Math.max(0, Math.min(100, Number.isFinite(percent) ? percent : 0))
  const fill = colorFor(clamped, thresholds.warn, thresholds.danger)
  const data = [{ name: 'value', value: clamped, fill }]

  return (
    <div className="relative flex h-full w-full items-center justify-center p-3">
      <ResponsiveContainer width="100%" height="100%">
        <RadialBarChart
          data={data}
          innerRadius="65%"
          outerRadius="100%"
          startAngle={210}
          endAngle={-30}
        >
          <PolarAngleAxis type="number" domain={[0, 100]} tick={false} />
          <RadialBar
            dataKey="value"
            background={{ fill: 'var(--color-surface-subtle)' }}
            cornerRadius={8}
            isAnimationActive={false}
          />
        </RadialBarChart>
      </ResponsiveContainer>
      <div
        className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center"
        aria-hidden
      >
        <p className="h2-b" style={{ color: fill }}>
          {label ?? `${clamped.toFixed(1)}%`}
        </p>
        {sublabel && <p className="caption-r text-fg-secondary">{sublabel}</p>}
      </div>
    </div>
  )
}
