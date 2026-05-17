'use client'

import { LATENCY_QUANTILE_COLORS, METRICS_COLORS } from '../constants'
import {
  seriesRangeToRows,
  useNginxActiveConnections,
  useNginxRequestRate,
  useSpringHttpErrorRatio,
  useSpringHttpLatency,
  useSpringHttpRps,
  type MetricsVizArgs,
} from '../hooks'

import { ChartFrame } from './ChartFrame'
import { MetricsLineChart } from './MetricsLineChart'
import { VizCard } from './VizCard'

type Props = {
  args: MetricsVizArgs
  onRetry: () => void
}

const formatRps = (value: number) =>
  `${value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })} req/s`
const formatMs = (value: number) =>
  `${(value * 1000).toLocaleString('ko-KR', { maximumFractionDigits: 1 })} ms`
const formatPercent = (value: number) =>
  `${(value * 100).toLocaleString('ko-KR', { maximumFractionDigits: 2 })}%`
const formatCount = (value: number) =>
  value.toLocaleString('ko-KR', { maximumFractionDigits: 0 })

// ============================================================
function NginxRequestRatePanel({ args, onRetry }: Props) {
  const state = useNginxRequestRate(args)
  const { rows, keys } = seriesRangeToRows(state.data?.series ?? [], () => 'rps')
  return (
    <VizCard title="Nginx 요청 처리율" subtitle="req/s" span={6}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          labelFor={() => 'req/s'}
          colorFor={() => METRICS_COLORS.accent}
          valueFormatter={formatRps}
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function NginxConnectionsPanel({ args, onRetry }: Props) {
  const state = useNginxActiveConnections(args)
  const { rows, keys } = seriesRangeToRows(state.data?.series ?? [], () => 'connections')
  return (
    <VizCard title="Nginx 활성 커넥션" subtitle="instant" span={6}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          labelFor={() => '활성 커넥션'}
          colorFor={() => METRICS_COLORS.secondary}
          valueFormatter={formatCount}
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function SpringHttpRpsPanel({ args, onRetry }: Props) {
  const state = useSpringHttpRps(args)
  const { rows, keys } = seriesRangeToRows(
    state.data?.series ?? [],
    (labels, index) => labels.service ?? labels.job ?? `series-${index}`,
  )
  return (
    <VizCard title="Spring HTTP RPS" subtitle="service별 5분 rate" span={6}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart rows={rows} seriesKeys={keys} valueFormatter={formatRps} />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function SpringHttpLatencyPanel({ args, onRetry }: Props) {
  const state = useSpringHttpLatency(args)
  const rows = state.data?.rows ?? []
  const keys = state.data?.keys ?? []
  return (
    <VizCard title="Spring 응답 latency" subtitle="p50 / p95 / p99" span={6}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          labelFor={(key) => `p${(Number(key) * 100).toFixed(0)}`}
          colorFor={(key) => LATENCY_QUANTILE_COLORS[key] ?? METRICS_COLORS.muted}
          valueFormatter={formatMs}
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function SpringHttpErrorRatioPanel({ args, onRetry }: Props) {
  const state = useSpringHttpErrorRatio(args)
  const { rows, keys } = seriesRangeToRows(state.data?.series ?? [], () => 'ratio')
  return (
    <VizCard title="Spring 에러율" subtitle="5xx / total (%)" span={12}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          labelFor={() => '5xx 비율'}
          colorFor={() => METRICS_COLORS.danger}
          valueFormatter={formatPercent}
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
export function RowRequestResponse({ args, onRetry }: Props) {
  return (
    <>
      <NginxRequestRatePanel args={args} onRetry={onRetry} />
      <NginxConnectionsPanel args={args} onRetry={onRetry} />
      <SpringHttpRpsPanel args={args} onRetry={onRetry} />
      <SpringHttpLatencyPanel args={args} onRetry={onRetry} />
      <SpringHttpErrorRatioPanel args={args} onRetry={onRetry} />
    </>
  )
}
