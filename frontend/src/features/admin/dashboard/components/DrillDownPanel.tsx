'use client'

import { AnimatePresence, motion } from 'motion/react'
import { X } from 'lucide-react'
import { useEffect, useState } from 'react'

import { postAdminLogsHistogram, postAdminLogsSearch } from '@/shared/apis'
import type {
  AdminLogsHistogramResponse,
  AdminLogsSearchResponse,
  AdminLogsTimeRange,
} from '@/shared/types'
import { formatKoreanDateTime } from '@/shared/utils'

import type { AnalyticsDrillDownState } from '../types'

type Props = {
  state: AnalyticsDrillDownState | null
  timeRange: AdminLogsTimeRange
  serviceQuery: string | undefined
  onClose: () => void
}

// 드릴다운 사이드 패널 — 우측에서 슬라이드 인.
//
// 차트 요소(막대/도넛 조각/heatmap 셀) 클릭 → useAnalyticsDrillDown.open 호출.
// 패널은 해당 dimension 필터를 적용해 search(50건) + histogram을 호출.
// 다른 차트 클릭 → state 교체 → 같은 패널이 내용만 swap.
//
// 이 PR에선 실데이터 viz가 KPI뿐이라 트리거가 없음 — 개발 환경의 mock 버튼이 set 호출.

const composeQuery = (parts: Array<string | undefined>): string => {
  const segments = parts.filter((segment): segment is string => Boolean(segment && segment.trim()))
  return segments.join(' AND ')
}

const formatBucketTime = (ts: string): string => {
  return formatKoreanDateTime(ts, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function DrillDownPanel({ state, timeRange, serviceQuery, onClose }: Props) {
  const [search, setSearch] = useState<AdminLogsSearchResponse | null>(null)
  const [histogram, setHistogram] = useState<AdminLogsHistogramResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!state) return
    let cancelled = false

    const run = async () => {
      if (!cancelled) {
        setIsLoading(true)
        setErrorMessage(null)
        setSearch(null)
        setHistogram(null)
      }
      try {
        const query = composeQuery([serviceQuery, state.extraQuery])
        const payload = {
          index: 'biz-events' as const,
          query: query || undefined,
          filters: state.dimensionFilters.length > 0 ? state.dimensionFilters : undefined,
          timeRange,
        }
        const [searchResponse, histogramResponse] = await Promise.all([
          postAdminLogsSearch({ ...payload, size: 50 }),
          postAdminLogsHistogram(payload),
        ])
        if (cancelled) return
        setSearch(searchResponse)
        setHistogram(histogramResponse)
        setIsLoading(false)
      } catch (caughtError) {
        if (cancelled) return
        const message =
          caughtError instanceof Error
            ? caughtError.message
            : '드릴다운 데이터를 불러오지 못했어요'
        setErrorMessage(message)
        setIsLoading(false)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [state, timeRange, serviceQuery])

  return (
    <AnimatePresence>
      {state && (
        <>
          <motion.div
            key="overlay"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            onClick={onClose}
            className="fixed inset-0 z-[var(--z-overlay)] bg-black/30"
            aria-hidden
          />
          <motion.aside
            key="panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="drilldown-title"
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', stiffness: 280, damping: 32 }}
            className="fixed inset-y-0 right-0 z-[var(--z-modal)] flex w-full max-w-md flex-col bg-surface-default shadow-lg"
          >
            <header className="flex shrink-0 items-center justify-between border-b border-border-default px-5 py-4">
              <div className="flex flex-col gap-1">
                <p className="caption-b text-fg-secondary">{state.vizId} · 드릴다운</p>
                <h2 id="drilldown-title" className="h4-b text-fg-primary">
                  {state.chartLabel}
                </h2>
                {state.description && (
                  <p className="caption-r text-fg-secondary">{state.description}</p>
                )}
              </div>
              <button
                type="button"
                onClick={onClose}
                aria-label="패널 닫기"
                className="rounded-[var(--radius-md)] p-1.5 text-fg-secondary transition-colors hover:bg-surface-subtle"
              >
                <X className="h-5 w-5" />
              </button>
            </header>

            <div className="flex-1 overflow-y-auto px-5 py-4">
              {isLoading && (
                <p className="body-r text-fg-secondary">불러오는 중…</p>
              )}
              {errorMessage && !isLoading && (
                <p className="body-r text-red-500" role="alert">
                  {errorMessage}
                </p>
              )}
              {!isLoading && !errorMessage && (
                <div className="flex flex-col gap-6">
                  <section>
                    <h3 className="caption-b mb-2 text-fg-secondary">시계열</h3>
                    {histogram && histogram.buckets.length > 0 ? (
                      <ul className="flex flex-col gap-1">
                        {histogram.buckets.slice(-8).map((bucket) => (
                          <li
                            key={bucket.ts}
                            className="caption-r flex justify-between text-fg-primary"
                          >
                            <span>{formatBucketTime(bucket.ts)}</span>
                            <span>{bucket.total.toLocaleString('ko-KR')}건</span>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="caption-r text-fg-disabled">데이터 없음</p>
                    )}
                  </section>

                  <section>
                    <h3 className="caption-b mb-2 text-fg-secondary">
                      최근 이벤트 (최대 50건)
                    </h3>
                    {search && search.hits.length > 0 ? (
                      <ul className="flex flex-col gap-2">
                        {search.hits.map((hit) => {
                          const eventName = String(hit.source.event_name ?? '—')
                          const timestamp = String(hit.source['@timestamp'] ?? '')
                          return (
                            <li
                              key={hit.id}
                              className="rounded-[var(--radius-md)] border border-border-default bg-surface-subtle p-2"
                            >
                              <p className="caption-b text-fg-primary">{eventName}</p>
                              <p className="caption-r text-fg-disabled">
                                {timestamp ? formatBucketTime(timestamp) : '—'}
                              </p>
                            </li>
                          )
                        })}
                      </ul>
                    ) : (
                      <p className="caption-r text-fg-disabled">데이터 없음</p>
                    )}
                  </section>
                </div>
              )}
            </div>
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  )
}
