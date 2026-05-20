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
}

const colorFor = (funnelKey: string): string => {
  const palette = CHART_FUNNEL_COLORS as Record<string, string>
  return palette[funnelKey] ?? CHART_STATUS_COLORS.muted
}

const labelFor = (funnelKey: string): string => FUNNEL_LABEL[funnelKey] ?? funnelKey

// 컴포넌트 이름은 호출부 호환을 위해 유지(ShareRateDonut)하지만 표현은 funnel별 가로 막대.
//
// shareRate = shared / goal 으로, shared는 각 도메인의 share/save/community_post
// 액션 성공 시점에 emit되는 명시적 result_shared 이벤트로 측정한다.
// 보조 라인에 공유·이탈 절대값을 함께 표기해 운영자가 도달→공유→이탈 흐름을 즉시 볼 수 있다.
export function ShareRateDonut({ state, onRetry, onDrillDown }: Props) {
  const data = (state.data ?? []).map((bucket) => {
    const shareRate = bucket.goal > 0 ? bucket.shared / bucket.goal : 0
    return {
      raw: bucket.value,
      name: labelFor(bucket.value),
      goal: bucket.goal,
      shared: bucket.shared,
      abandoned: bucket.abandoned,
      shareRate,
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
          const percent = Math.round(entry.shareRate * 100)
          return (
            <button
              key={entry.raw}
              type="button"
              onClick={() =>
                onDrillDown({
                  vizId: 'I6',
                  chartLabel: `결과 도달 후 공유: ${entry.name}`,
                  dimensionFilters: [
                    {
                      field: 'metadata.funnel_name',
                      value: entry.raw,
                      negate: false,
                    },
                  ],
                  extraQuery: 'event_name:result_shared',
                  description:
                    '결과 페이지 도달 후 share/save/community_post 액션을 실제로 한 세션의 raw 이벤트',
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
                    width: `${Math.min(entry.shareRate, 1) * 100}%`,
                    backgroundColor: color,
                  }}
                />
              </div>
              <div className="flex items-center justify-between gap-2">
                <span className="caption-r text-fg-secondary tabular-nums">
                  공유 {entry.shared.toLocaleString('ko-KR')} / 도달{' '}
                  {entry.goal.toLocaleString('ko-KR')}
                </span>
                <span className="caption-r text-fg-disabled tabular-nums">
                  이탈 {entry.abandoned.toLocaleString('ko-KR')}
                </span>
              </div>
            </button>
          )
        })}
      </div>
    </ChartFrame>
  )
}
