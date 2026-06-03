'use client'

import { CHART_STATUS_COLORS } from '..'
import type { AnalyticsDrillDownState, AnalyticsKpiState } from '../types'
import type { I13Phase } from '../hooks'

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

// 단계 직전 평균 체류시간 — 30초·60초 두 임계치를 가이드라인으로 노출해 막대 길이만 보고도
// 어느 구간에 속하는지 한눈에 파악되게 한다. 카드 큰 숫자만 보여주는 기존 표현은 phase
// 간 비교가 어렵고 "이게 길다는 건지 짧다는 건지" 직관적이지 않다는 피드백을 받아 교체.
const WARN_THRESHOLD_SEC = 30
const DANGER_THRESHOLD_SEC = 60
const MIN_AXIS_MAX_SEC = 90

function severityColor(avgSec: number): string {
  if (avgSec >= DANGER_THRESHOLD_SEC) return CHART_STATUS_COLORS.danger
  if (avgSec >= WARN_THRESHOLD_SEC) return CHART_STATUS_COLORS.warn
  return CHART_STATUS_COLORS.good
}

function severityLabel(avgSec: number): string {
  if (avgSec >= DANGER_THRESHOLD_SEC) return '오래 머무름'
  if (avgSec >= WARN_THRESHOLD_SEC) return '보통'
  return '빠른 이탈'
}

export function AbandonElapsedCards({ state, onRetry, onDrillDown }: Props) {
  const phases = (state.data ?? []).slice().sort((a, b) => a.order - b.order)
  const isEmpty = phases.length === 0 || phases.every((phase) => phase.count === 0)
  const axisMax = Math.max(
    MIN_AXIS_MAX_SEC,
    ...phases.map((phase) => phase.avgSec * 1.1),
  )
  const warnLeft = (WARN_THRESHOLD_SEC / axisMax) * 100
  const dangerLeft = (DANGER_THRESHOLD_SEC / axisMax) * 100

  return (
    <ChartFrame
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      isEmpty={isEmpty}
      onRetry={onRetry}
    >
      <div className="flex h-full flex-col gap-3 p-4">
        <div className="caption-r flex flex-wrap items-center gap-3 text-fg-secondary">
          <span className="inline-flex items-center gap-1">
            <span
              className="inline-block h-2 w-2 rounded-full"
              style={{ backgroundColor: CHART_STATUS_COLORS.good }}
            />
            ~30초 빠른 이탈
          </span>
          <span className="inline-flex items-center gap-1">
            <span
              className="inline-block h-2 w-2 rounded-full"
              style={{ backgroundColor: CHART_STATUS_COLORS.warn }}
            />
            30~60초 보통
          </span>
          <span className="inline-flex items-center gap-1">
            <span
              className="inline-block h-2 w-2 rounded-full"
              style={{ backgroundColor: CHART_STATUS_COLORS.danger }}
            />
            60초~ 오래 머무름
          </span>
        </div>

        <div className="flex flex-1 flex-col justify-around gap-3">
          {phases.map((phase) => {
            const barWidth = Math.min((phase.avgSec / axisMax) * 100, 100)
            const color = severityColor(phase.avgSec)
            return (
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
                className="flex flex-col gap-1.5 rounded-[var(--radius-md)] p-2 text-left transition-colors hover:bg-surface-subtle"
              >
                <div className="flex items-baseline justify-between gap-2">
                  <span className="body-m text-fg-primary">{phase.label}</span>
                  <span className="caption-r text-fg-secondary tabular-nums">
                    {phase.count.toLocaleString('ko-KR')}건 이탈
                  </span>
                </div>
                <div className="relative h-7 w-full overflow-hidden rounded-[var(--radius-sm)] bg-surface-subtle">
                  <div
                    aria-hidden
                    className="absolute top-0 h-full border-l border-dashed border-fg-disabled/70"
                    style={{ left: `${warnLeft}%` }}
                  />
                  <div
                    aria-hidden
                    className="absolute top-0 h-full border-l border-dashed border-fg-disabled/70"
                    style={{ left: `${dangerLeft}%` }}
                  />
                  <div
                    className="h-full rounded-[var(--radius-sm)] transition-all"
                    style={{ width: `${barWidth}%`, backgroundColor: color }}
                  />
                  <span className="absolute inset-0 flex items-center justify-between px-2">
                    <span
                      className="caption-b tabular-nums"
                      style={{ color: barWidth > 12 ? '#fff' : color }}
                    >
                      {phase.avgSec.toFixed(1)}초
                    </span>
                    <span
                      className="caption-r"
                      style={{ color }}
                    >
                      {severityLabel(phase.avgSec)}
                    </span>
                  </span>
                </div>
              </button>
            )
          })}
        </div>
      </div>
    </ChartFrame>
  )
}
