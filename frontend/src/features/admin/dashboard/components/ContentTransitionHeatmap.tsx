'use client'

import { useMemo, useState } from 'react'

import { cn } from '@/shared/libs'

import type { AnalyticsDrillDownState, AnalyticsKpiState } from '../types'
import type { I4Cell } from '../hooks/useAnalyticsCharts'

import { ChartFrame } from './ChartFrame'

type Props = {
  state: AnalyticsKpiState<I4Cell[]>
  onRetry: () => void
  onDrillDown: (next: AnalyticsDrillDownState) => void
}

// 6 카테고리 고정 순서. PATH_TO_CONTENT 결과와 정렬.
const CATEGORIES = ['홈', '릴레이드로잉', '플립북', '커뮤니티', '오늘의 운세', '기타'] as const

// 카테고리별 path substring. 백엔드는 Lucene `\` 이스케이프를 거부하므로 leading `/`
// 없는 wildcard substring으로 작성. keyword field에 대해 `*foo*`로 동작.
const PATH_PREFIX: Record<string, string> = {
  홈: '*hub* OR *main* OR *home*',
  릴레이드로잉: '*relay-drawing*',
  플립북: '*flipbook*',
  커뮤니티: '*community*',
  '오늘의 운세': '*fortune*',
}

// purples scheme — 5 단계.
const PURPLE_STOPS = [
  'hsl(258 30% 90%)',
  'hsl(258 50% 80%)',
  'hsl(258 60% 70%)',
  'hsl(258 70% 60%)',
  'hsl(258 80% 50%)',
]

const colorFor = (count: number, max: number): string => {
  if (max <= 0 || count <= 0) return 'var(--color-surface-subtle)'
  const ratio = count / max
  if (ratio < 0.2) return PURPLE_STOPS[0]
  if (ratio < 0.4) return PURPLE_STOPS[1]
  if (ratio < 0.6) return PURPLE_STOPS[2]
  if (ratio < 0.8) return PURPLE_STOPS[3]
  return PURPLE_STOPS[4]
}

const textColorFor = (count: number, max: number): string => {
  if (max <= 0 || count <= 0) return 'var(--color-fg-disabled)'
  return count / max >= 0.5 ? 'var(--color-fg-inverse)' : 'var(--color-fg-primary)'
}

export function ContentTransitionHeatmap({ state, onRetry, onDrillDown }: Props) {
  const cells = useMemo(() => state.data ?? [], [state.data])
  const [hover, setHover] = useState<{ from: string; to: string; count: number } | null>(null)

  const lookup = useMemo(() => {
    const map = new Map<string, number>()
    for (const cell of cells) map.set(`${cell.from}||${cell.to}`, cell.count)
    return map
  }, [cells])

  const maxCount = useMemo(() => cells.reduce((max, cell) => Math.max(max, cell.count), 0), [cells])
  const isEmpty = maxCount === 0

  return (
    <ChartFrame
      isLoading={state.isLoading}
      errorMessage={state.errorMessage}
      isEmpty={isEmpty}
      onRetry={onRetry}
    >
      <div className="flex h-full flex-col gap-2 p-3">
        <div className="grid grid-cols-[80px_repeat(6,1fr)] gap-1">
          <div />
          {CATEGORIES.map((category) => (
            <div
              key={`col-${category}`}
              className="caption-b text-center text-fg-secondary"
            >
              {category}
            </div>
          ))}
          {CATEGORIES.map((rowCategory) => (
            <div key={`row-${rowCategory}`} className="contents">
              <div className="caption-b flex items-center justify-end pr-2 text-fg-secondary">
                {rowCategory}
              </div>
              {CATEGORIES.map((columnCategory) => {
                const count = lookup.get(`${columnCategory}||${rowCategory}`) ?? 0
                const isSameCategory = columnCategory === rowCategory
                return (
                  <button
                    key={`cell-${rowCategory}-${columnCategory}`}
                    type="button"
                    disabled={isSameCategory || count === 0}
                    onMouseEnter={() => setHover({ from: columnCategory, to: rowCategory, count })}
                    onMouseLeave={() => setHover(null)}
                    onClick={() => {
                      if (isSameCategory || count === 0) return
                      const fromPrefix = PATH_PREFIX[columnCategory]
                      const toPrefix = PATH_PREFIX[rowCategory]
                      const queryParts: string[] = ['event_name:page_view']
                      if (fromPrefix) queryParts.push(`prev_path:(${fromPrefix})`)
                      if (toPrefix) queryParts.push(`path:(${toPrefix})`)
                      onDrillDown({
                        vizId: 'I4',
                        chartLabel: `${columnCategory} → ${rowCategory}`,
                        dimensionFilters: [],
                        extraQuery: queryParts.join(' AND '),
                      })
                    }}
                    style={{
                      backgroundColor: isSameCategory
                        ? 'var(--color-surface-default)'
                        : colorFor(count, maxCount),
                      color: textColorFor(count, maxCount),
                    }}
                    className={cn(
                      'caption-b flex aspect-square items-center justify-center rounded-[var(--radius-sm)] transition-opacity',
                      !isSameCategory && count > 0 && 'cursor-pointer hover:opacity-80',
                    )}
                  >
                    {isSameCategory ? '·' : count > 0 ? count.toLocaleString('ko-KR') : ''}
                  </button>
                )
              })}
            </div>
          ))}
        </div>
        <p className="caption-r mt-auto text-fg-disabled">
          {hover
            ? `${hover.from} → ${hover.to} : ${hover.count.toLocaleString('ko-KR')}건`
            : '셀: 이전 컨텐츠(열) → 다음 컨텐츠(행) 이동 세션 수. 같은 컨텐츠 내 이동 제외.'}
        </p>
      </div>
    </ChartFrame>
  )
}
