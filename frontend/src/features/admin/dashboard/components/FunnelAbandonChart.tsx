'use client'

import { ChevronRight } from 'lucide-react'

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

// funnel별로 단계 카드를 화살표로 잇는 깔때기 시각화.
// - 카드 너비를 첫 단계 대비 잔존 비율로 조절해 진짜 "깔때기"가 좁아지는 모양을 만든다.
// - 색 opacity도 점진적으로 옅어져 잔존 감소를 강조한다.
// - 카드 위에 잔존 수와 직전 단계 대비 잔존율(%)을 함께 노출해 의미를 한눈에 전달한다.
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
      <div className="flex h-full flex-col gap-4 overflow-y-auto p-4">
        {funnels.map((funnel) => {
          const baseCount = funnel.steps[0]?.count ?? 0
          const color = colorFor(funnel.name)
          return (
            <div key={funnel.name} className="flex flex-col gap-1.5">
              <div className="caption-b" style={{ color }}>
                {labelFor(funnel.name)}
              </div>
              <div className="flex items-stretch gap-0">
                {funnel.steps.map((step, index) => {
                  const ratioFromStart =
                    baseCount > 0 ? step.count / baseCount : 0
                  const ratioFromPrev =
                    index === 0 || funnel.steps[index - 1].count === 0
                      ? 1
                      : step.count / funnel.steps[index - 1].count
                  const widthBasis = Math.max(ratioFromStart * 100, 16)
                  const opacity = 0.35 + ratioFromStart * 0.65
                  return (
                    <div
                      key={step.name}
                      className="flex items-stretch"
                      style={{ flex: `${widthBasis} 0 0` }}
                    >
                      <button
                        type="button"
                        onClick={() =>
                          onDrillDown({
                            vizId: 'I3',
                            chartLabel: `${labelFor(funnel.name)} · ${step.name}`,
                            dimensionFilters: [
                              {
                                field: 'metadata.funnel_name',
                                value: funnel.name,
                                negate: false,
                              },
                              {
                                field: 'metadata.step_name',
                                value: step.name,
                                negate: false,
                              },
                            ],
                            extraQuery: 'event_name:funnel_step_completed',
                          })
                        }
                        style={{ backgroundColor: color, opacity }}
                        className="flex flex-1 flex-col items-center justify-center gap-0.5 rounded-[var(--radius-sm)] px-2 py-2 text-center transition-opacity hover:opacity-100"
                      >
                        <span className="caption-b truncate text-white">
                          {step.count.toLocaleString('ko-KR')}
                        </span>
                        <span className="caption-r truncate text-white/85">
                          {step.name}
                        </span>
                        {index > 0 && (
                          <span className="caption-r text-white/75">
                            잔존 {Math.round(ratioFromPrev * 100)}%
                          </span>
                        )}
                      </button>
                      {index < funnel.steps.length - 1 && (
                        <div className="flex shrink-0 items-center px-1 text-fg-secondary">
                          <ChevronRight className="h-3.5 w-3.5" />
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>
          )
        })}
      </div>
    </ChartFrame>
  )
}
