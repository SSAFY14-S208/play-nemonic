'use client'

import { CHART_FUNNEL_COLORS, CHART_STATUS_COLORS } from '../constants'
import type { AnalyticsDrillDownState, AnalyticsKpiState } from '../types'
import type { I6Bucket } from '../hooks/useAnalyticsCharts'

import { ChartFrame } from './ChartFrame'

type Props = {
  state: AnalyticsKpiState<I6Bucket[]>
  onRetry: () => void
  onDrillDown: (next: AnalyticsDrillDownState) => void
}

const FUNNEL_LABEL: Record<string, string> = {
  relay_room_creation: '릴레이드로잉',
  flipbook_room_creation: '플립북',
  community_memo_posting: '커뮤니티 메모',
  fortune_creation: '오늘의 운세',
  gallery_save_share: '갤러리·공유',
  infinite_canvas_creation: '무한 캔버스',
}

const colorFor = (funnelKey: string): string => {
  const palette = CHART_FUNNEL_COLORS as Record<string, string>
  return palette[funnelKey] ?? CHART_STATUS_COLORS.muted
}

const labelFor = (funnelKey: string): string => FUNNEL_LABEL[funnelKey] ?? funnelKey

// 컴포넌트 이름은 호출부 호환을 위해 유지(ShareRateDonut)하지만 표현은 funnel별 가로 막대.
//
// viz의 의미는 "결과 도달 후 이탈 비율" = abandoned / goal 이다. 진짜 공유율을 측정하려면
// share/save 액션 시점에 명시적 `result_shared` 이벤트 emit이 필요한데, 현재 그 트래킹이
// 없어서 이전 구현(shared = goal - abandoned 근사치)이 abandoned가 0일 때 무조건 100%로
// 표시되는 문제가 있었다. 의미상 정확한 메트릭으로 재정의하고, 진짜 공유율 viz는 추후
// result_shared 이벤트 트래킹이 추가되면 별도 viz로 신설한다.
export function ShareRateDonut({ state, onRetry, onDrillDown }: Props) {
  const data = (state.data ?? []).map((bucket) => {
    const abandonRate = bucket.goal > 0 ? bucket.abandoned / bucket.goal : 0
    return {
      raw: bucket.value,
      name: labelFor(bucket.value),
      goal: bucket.goal,
      abandoned: bucket.abandoned,
      abandonRate,
    }
  })
  const isEmpty = data.length === 0 || data.every((entry) => entry.goal === 0)

  return (
    <ChartFrame
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      isEmpty={isEmpty}
      onRetry={onRetry}
    >
      <div className="flex h-full flex-col gap-2 overflow-y-auto p-4">
        {data.map((entry) => {
          const color = colorFor(entry.raw)
          const percent = Math.round(entry.abandonRate * 100)
          return (
            <button
              key={entry.raw}
              type="button"
              onClick={() =>
                onDrillDown({
                  vizId: 'I6',
                  chartLabel: `결과 도달 후 이탈: ${entry.name}`,
                  dimensionFilters: [
                    {
                      field: 'metadata.funnel_name',
                      value: entry.raw,
                      negate: false,
                    },
                  ],
                  extraQuery: 'event_name:result_share_abandoned',
                  description:
                    '결과 페이지에 도달한 뒤 공유/저장 액션 없이 이탈한 세션의 raw 이벤트',
                })
              }
              className="flex flex-col gap-1 rounded-[var(--radius-md)] p-2 text-left transition-colors hover:bg-surface-subtle"
            >
              <div className="flex items-center justify-between gap-2">
                <span className="caption-b truncate" style={{ color }}>
                  {entry.name}
                </span>
                <span className="body-m text-fg-primary tabular-nums">
                  {percent}%
                </span>
              </div>
              <div className="relative h-3 w-full overflow-hidden rounded-full bg-surface-subtle">
                <div
                  className="h-full rounded-full transition-all"
                  style={{
                    width: `${Math.min(entry.abandonRate, 1) * 100}%`,
                    backgroundColor: color,
                  }}
                />
              </div>
              <div className="flex items-center justify-between gap-2">
                <span className="caption-r text-fg-secondary tabular-nums">
                  이탈 {entry.abandoned.toLocaleString('ko-KR')} / 도달{' '}
                  {entry.goal.toLocaleString('ko-KR')}
                </span>
              </div>
            </button>
          )
        })}
      </div>
    </ChartFrame>
  )
}
