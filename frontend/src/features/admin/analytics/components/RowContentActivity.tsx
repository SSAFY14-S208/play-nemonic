'use client'

import { FUNNEL_COLORS, METRICS_COLORS } from '../constants'
import {
  seriesRangeToRows,
  useActiveContent,
  useTopEvents,
  type MetricsVizArgs,
} from '../hooks'

import { ChartFrame } from './ChartFrame'
import { MetricsLineChart } from './MetricsLineChart'
import { VizCard } from './VizCard'

type Props = {
  args: MetricsVizArgs
  onRetry: () => void
}

const formatCount = (value: number) =>
  value.toLocaleString('ko-KR', { maximumFractionDigits: 0 })

// ============================================================
function ActiveContentPanel({ args, onRetry }: Props) {
  const state = useActiveContent(args)
  const { rows, keys } = seriesRangeToRows(
    state.data?.series ?? [],
    (labels, index) => labels.content_type ?? `series-${index}`,
  )
  return (
    <VizCard title="진행 중 콘텐츠" subtitle="content_type별 활성 room/canvas" span={8} minHeight={260}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          labelFor={(key) => key}
          colorFor={(key) => FUNNEL_COLORS[key] ?? METRICS_COLORS.accent}
          valueFormatter={formatCount}
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function TopEventsPanel({ args, onRetry }: Props) {
  const state = useTopEvents(args)
  const top = (state.data ?? []).slice(0, 8)
  return (
    <VizCard title="주요 이벤트 발생 수" subtitle="top 10 event_name" span={4} minHeight={260}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && top.length === 0}
        onRetry={onRetry}
      >
        <ul className="flex h-full flex-col gap-2 overflow-y-auto p-3">
          {top.map((bucket) => (
            <li
              key={bucket.value}
              className="flex items-center justify-between rounded-[var(--radius-md)] bg-surface-subtle px-3 py-2"
            >
              <span className="caption-b break-all text-fg-primary">{bucket.value}</span>
              <span className="caption-r text-fg-secondary">
                {formatCount(bucket.count)}
              </span>
            </li>
          ))}
        </ul>
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
export function RowContentActivity({ args, onRetry }: Props) {
  return (
    <>
      <ActiveContentPanel args={args} onRetry={onRetry} />
      <TopEventsPanel args={args} onRetry={onRetry} />
    </>
  )
}
