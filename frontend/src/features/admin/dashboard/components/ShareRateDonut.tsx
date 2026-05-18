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
// 각 funnel의 share_rate = shared / goal 는 독립 비율이라 도넛 segments로 합산하면 의미가 깨진다.
// funnel 간 절대 비교를 직관적으로 하기 위해 0~100% 가로 막대로 나열한다.
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
                  chartLabel: `공유 도달: ${entry.name}`,
                  dimensionFilters: [
                    {
                      field: 'metadata.funnel_name',
                      value: entry.raw,
                      negate: false,
                    },
                  ],
                  extraQuery: 'event_name:funnel_goal_reached',
                  description: '결과 도달 이후 공유까지 도달한 세션의 raw 이벤트',
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
                    width: `${entry.shareRate * 100}%`,
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
