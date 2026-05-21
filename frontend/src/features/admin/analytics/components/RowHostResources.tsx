'use client'

import { METRICS_COLORS } from '../constants'
import {
  seriesRangeToRows,
  useHostCpu,
  useHostDisk,
  useHostMemory,
  useHostNetworkIo,
  type MetricsVizArgs,
} from '../hooks'

import { ChartFrame } from './ChartFrame'
import { MetricsGauge } from './MetricsGauge'
import { MetricsLineChart } from './MetricsLineChart'
import { VizCard } from './VizCard'

type Props = {
  args: MetricsVizArgs
  onRetry: () => void
}

const formatPercent = (value: number) =>
  `${value.toLocaleString('ko-KR', { maximumFractionDigits: 1 })}%`
const formatBytesPerSec = (value: number) => {
  if (value < 1024) return `${value.toFixed(0)} B/s`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB/s`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB/s`
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB/s`
}

// ============================================================
function HostCpuPanel({ args, onRetry }: Props) {
  const state = useHostCpu(args)
  const { rows, keys } = seriesRangeToRows(
    state.data?.series ?? [],
    (labels, index) => labels.instance ?? `instance-${index}`,
  )
  return (
    <VizCard title="Host CPU 사용량" subtitle="instance별 %" span={6} minHeight={240}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          valueFormatter={formatPercent}
          yDomain={[0, 100]}
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function HostMemoryPanel({ args, onRetry }: Props) {
  const state = useHostMemory(args)
  const value = state.data?.series[0]?.value ?? null
  return (
    <VizCard title="Host 메모리" subtitle="사용 중 %" span={3} minHeight={240}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && value === null}
        onRetry={onRetry}
      >
        <MetricsGauge percent={value ?? 0} sublabel="사용 중" />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function HostDiskPanel({ args, onRetry }: Props) {
  const state = useHostDisk(args)
  const value = state.data?.series[0]?.value ?? null
  return (
    <VizCard title="디스크 사용량" subtitle="/ 파티션 %" span={3} minHeight={240}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && value === null}
        onRetry={onRetry}
      >
        <MetricsGauge percent={value ?? 0} sublabel="사용 중" />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function HostNetworkIoPanel({ args, onRetry }: Props) {
  const state = useHostNetworkIo(args)
  const rows = state.data?.rows ?? []
  const keys = state.data?.keys ?? []
  return (
    <VizCard title="네트워크 I/O" subtitle="bytes/s — in / out" span={12} minHeight={240}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          labelFor={(key) => (key === 'in' ? '수신' : '송신')}
          colorFor={(key) => (key === 'in' ? METRICS_COLORS.secondary : METRICS_COLORS.tertiary)}
          valueFormatter={formatBytesPerSec}
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
export function RowHostResources({ args, onRetry }: Props) {
  return (
    <>
      <HostCpuPanel args={args} onRetry={onRetry} />
      <HostMemoryPanel args={args} onRetry={onRetry} />
      <HostDiskPanel args={args} onRetry={onRetry} />
      <HostNetworkIoPanel args={args} onRetry={onRetry} />
    </>
  )
}
