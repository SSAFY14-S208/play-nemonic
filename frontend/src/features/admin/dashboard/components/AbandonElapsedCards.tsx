'use client'

import { CHART_STATUS_COLORS } from '../constants'
import type { AnalyticsDrillDownState, AnalyticsKpiState } from '../types'
import type { I13Phase } from '../hooks/useAnalyticsCharts'

import { ChartFrame } from './ChartFrame'

type Props = {
  state: AnalyticsKpiState<I13Phase[]>
  onRetry: () => void
  onDrillDown: (next: AnalyticsDrillDownState) => void
}

const PHASE_EVENT: Record<string, string> = {
  lobby: 'room_lobby_abandoned',
  creation: 'creation_abandoned',
  result: 'result_share_abandoned',
}

export function AbandonElapsedCards({ state, onRetry, onDrillDown }: Props) {
  const phases = (state.data ?? []).slice().sort((a, b) => a.order - b.order)
  const isEmpty = phases.length === 0 || phases.every((phase) => phase.count === 0)

  return (
    <ChartFrame
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      isEmpty={isEmpty}
      onRetry={onRetry}
    >
      <div className="grid h-full grid-cols-1 gap-3 p-4 md:grid-cols-3">
        {phases.map((phase) => (
          <button
            key={phase.name}
            type="button"
            onClick={() => {
              const eventName = PHASE_EVENT[phase.name] ?? ''
              onDrillDown({
                vizId: 'I13',
                chartLabel: `이탈 단계: ${phase.label}`,
                dimensionFilters: [],
                extraQuery: eventName ? `event_name:${eventName}` : undefined,
              })
            }}
            className="flex flex-col items-center justify-center gap-1 rounded-[var(--radius-md)] bg-surface-subtle p-4 text-center transition-colors hover:bg-surface-default"
          >
            <p className="caption-b text-fg-secondary">{phase.label}</p>
            <p
              className="h1-b"
              style={{
                color:
                  phase.avgSec >= 60
                    ? CHART_STATUS_COLORS.danger
                    : phase.avgSec >= 30
                      ? CHART_STATUS_COLORS.warn
                      : CHART_STATUS_COLORS.accent,
              }}
            >
              {phase.avgSec.toFixed(1)}초
            </p>
            <p className="caption-r text-fg-disabled">
              {phase.count.toLocaleString('ko-KR')}건 이탈
            </p>
          </button>
        ))}
      </div>
    </ChartFrame>
  )
}
