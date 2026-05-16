'use client'

import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { Formatter } from 'recharts/types/component/DefaultTooltipContent'

import { CHART_FUNNEL_COLORS, CHART_STATUS_COLORS } from '../constants'
import type { AnalyticsDrillDownState, AnalyticsKpiState } from '../types'
import type { I3Funnel } from '../hooks/useAnalyticsCharts'

import { ChartFrame } from './ChartFrame'

type Props = {
  state: AnalyticsKpiState<I3Funnel[]>
  onRetry: () => void
  onDrillDown: (next: AnalyticsDrillDownState) => void
}

const FUNNEL_LABEL: Record<string, string> = {
  relay_room_creation: '릴레이드로잉',
  flipbook_room_creation: '플립북',
  community_memo_posting: '커뮤니티 메모',
  fortune_creation: '오늘의 운세',
  gallery_save_share: '갤러리·공유',
}

const colorFor = (funnelKey: string): string => {
  const palette = CHART_FUNNEL_COLORS as Record<string, string>
  return palette[funnelKey] ?? CHART_STATUS_COLORS.muted
}

const labelFor = (funnelKey: string): string => FUNNEL_LABEL[funnelKey] ?? funnelKey

// 각 funnel을 row 1개의 가로 막대 차트로 facet. funnel 수가 적어(5개 이하) row마다
// 독립 BarChart를 grid로 배치하는 것이 recharts 기준 가장 안정적인 facet 구현.

export function FunnelAbandonChart({ state, onRetry, onDrillDown }: Props) {
  const funnels = state.data ?? []
  const isEmpty =
    funnels.length === 0 || funnels.every((funnel) => funnel.steps.length === 0)

  return (
    <ChartFrame
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      isEmpty={isEmpty}
      onRetry={onRetry}
    >
      <div className="flex h-full flex-col gap-2 overflow-y-auto p-3">
        {funnels.map((funnel) => {
          const data = funnel.steps.map((step) => ({
            stepName: step.name,
            count: step.count,
          }))
          return (
            <div key={funnel.name} className="flex items-center gap-2">
              <span
                className="caption-b w-28 shrink-0 text-fg-primary"
                style={{ color: colorFor(funnel.name) }}
              >
                {labelFor(funnel.name)}
              </span>
              <div className="h-16 flex-1">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={data}
                    layout="vertical"
                    margin={{ top: 4, right: 12, bottom: 4, left: 80 }}
                  >
                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke="var(--color-border-default)"
                    />
                    <XAxis
                      type="number"
                      stroke="var(--color-fg-secondary)"
                      tick={{ fontSize: 9 }}
                      hide
                    />
                    <YAxis
                      type="category"
                      dataKey="stepName"
                      width={80}
                      stroke="var(--color-fg-secondary)"
                      tick={{ fontSize: 10 }}
                    />
                    <Tooltip
                      formatter={
                        ((value) => {
                          const count =
                            typeof value === 'number' ? value : Number(value ?? 0)
                          return [`${count.toLocaleString('ko-KR')}건`, '잔존']
                        }) satisfies Formatter
                      }
                    />
                    <Bar
                      dataKey="count"
                      fill={colorFor(funnel.name)}
                      radius={[0, 3, 3, 0]}
                      cursor="pointer"
                      onClick={(entry) => {
                        if (!entry || !entry.payload) return
                        const stepName = entry.payload.stepName as string
                        onDrillDown({
                          vizId: 'I3',
                          chartLabel: `${labelFor(funnel.name)} · ${stepName}`,
                          dimensionFilters: [
                            {
                              field: 'metadata.funnel_name',
                              value: funnel.name,
                              negate: false,
                            },
                            {
                              field: 'metadata.step_name',
                              value: stepName,
                              negate: false,
                            },
                          ],
                          extraQuery: 'event_name:funnel_step_completed',
                        })
                      }}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )
        })}
      </div>
    </ChartFrame>
  )
}
