'use client'

import { SERVICE_UP_TARGETS } from '../constants'
import { useServiceUpStatus, type MetricsVizArgs } from '../hooks'

import { ServiceStatusDot } from './ServiceStatusDot'
import { VizCard } from './VizCard'

type Props = {
  args: MetricsVizArgs
}

// Row 1 — 서비스 up/down 6개. 한 VizCard 안에 6 stat grid.
// 개별 panel은 자체 hook으로 조회. 일부 panel 에러도 다른 stat은 그대로 보임.

function ServiceStatusEntry({
  args,
  target,
}: {
  args: MetricsVizArgs
  target: { job: string; label: string }
}) {
  const state = useServiceUpStatus(args, target.job)
  const status: 'up' | 'down' | 'unknown' =
    state.isLoading || !state.data
      ? 'unknown'
      : state.data.unknown
        ? 'unknown'
        : state.data.up
          ? 'up'
          : 'down'
  return <ServiceStatusDot label={target.label} status={status} />
}

export function RowServiceStatus({ args }: Props) {
  return (
    <VizCard title="서비스 상태" subtitle="Prometheus targets — up=1 / down=0" span={12} minHeight={160}>
      <div className="grid h-full grid-cols-3 gap-2 p-3 md:grid-cols-6">
        {SERVICE_UP_TARGETS.map((target) => (
          <ServiceStatusEntry key={target.job} args={args} target={target} />
        ))}
      </div>
    </VizCard>
  )
}
