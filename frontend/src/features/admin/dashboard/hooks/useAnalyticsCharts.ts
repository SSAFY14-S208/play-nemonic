'use client'

import { useEffect, useState } from 'react'

import {
  postAdminLogsCompositeBuckets,
  postAdminLogsFieldSummary,
  postAdminLogsFilteredMetrics,
  postAdminLogsHistogram,
  postAdminLogsTermsWithMetric,
  postAdminLogsTermsWithSubs,
} from '@/shared/apis'
import type {
  AdminLogsFilter,
  AdminLogsTimeRange,
} from '@/shared/types'

import type { AnalyticsKpiState } from '../types'

// 10개 viz의 데이터 fetch 훅 모음. 각 훅은 동일한 KpiFetcherArgs를 받아 의존성으로
// timeRange/serviceFilters/serviceQuery/refreshNonce 변경 시 자동 재호출한다.
//
// useEffect 본문에서 async IIFE로 fetch — React Compiler 룰 회피.
// cancelled flag로 unmount/의존성 변경 시 stale setState 차단.

export type AnalyticsVizArgs = {
  timeRange: AdminLogsTimeRange
  serviceFilters: AdminLogsFilter[]
  serviceQuery: string | undefined
  refreshNonce: number
}

const composeQuery = (parts: Array<string | undefined>): string => {
  const segments = parts.filter((segment): segment is string => Boolean(segment && segment.trim()))
  return segments.join(' AND ')
}

const errorMessage = (caughtError: unknown): string => {
  if (caughtError instanceof Error && caughtError.message) return caughtError.message
  return '데이터를 불러오지 못했어요'
}

// 공통 useEffect 패턴 — fetcher Promise를 wrapping해 loading/error 상태 관리.
function useAnalyticsFetcher<T>(
  fetcher: () => Promise<T>,
  deps: ReadonlyArray<unknown>,
): AnalyticsKpiState<T> {
  const [state, setState] = useState<AnalyticsKpiState<T>>({
    data: null,
    isLoading: true,
    errorMessage: null,
  })

  useEffect(() => {
    let cancelled = false

    const run = async () => {
      if (!cancelled) {
        setState((previous) => ({ ...previous, isLoading: true, errorMessage: null }))
      }
      try {
        const data = await fetcher()
        if (cancelled) return
        setState({ data, isLoading: false, errorMessage: null })
      } catch (caughtError) {
        if (cancelled) return
        setState({ data: null, isLoading: false, errorMessage: errorMessage(caughtError) })
      }
    }

    void run()
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return state
}

// ============================================================
// 채널 viz — I7 유입 경로 비율 (도넛)
// field-summary(metadata.entry_type) on event_name:landing_source_detected.
// ============================================================
export type I7Bucket = { value: string; count: number }

export function useI7EntryChannel(args: AnalyticsVizArgs) {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  return useAnalyticsFetcher<I7Bucket[]>(
    async () => {
      const response = await postAdminLogsFieldSummary({
        index: 'biz-events',
        query: composeQuery([serviceQuery, 'event_name:landing_source_detected']) || undefined,
        filters: serviceFilters.length > 0 ? serviceFilters : undefined,
        timeRange,
        fields: ['metadata.entry_type'],
        size: 10,
      })
      return response.fields['metadata.entry_type'] ?? []
    },
    [timeRange, serviceFilters, serviceQuery, refreshNonce],
  )
}

// ============================================================
// 채널 viz — I8 SNS 유입 비율 (도넛)
// field-summary(metadata.referrer, size:30) on entry_type:social.
// host별 raw → 클라이언트에서 SNS 라벨로 categorize.
// ============================================================
export type I8Bucket = { value: string; count: number }

export function useI8SnsEntry(args: AnalyticsVizArgs) {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  return useAnalyticsFetcher<I8Bucket[]>(
    async () => {
      const response = await postAdminLogsFieldSummary({
        index: 'biz-events',
        query: composeQuery([
          serviceQuery,
          'event_name:landing_source_detected',
          'metadata.entry_type:social',
        ]) || undefined,
        filters: serviceFilters.length > 0 ? serviceFilters : undefined,
        timeRange,
        fields: ['metadata.referrer'],
        size: 30,
      })
      return response.fields['metadata.referrer'] ?? []
    },
    [timeRange, serviceFilters, serviceQuery, refreshNonce],
  )
}

// ============================================================
// 컨텐츠 viz — I2 컨텐츠별 완주율 (가로 막대)
// terms-with-subs(funnel_name) + sub-filter(started/completed).
// started 분모는 funnel_step_completed의 첫 단계(settings/birth_info).
// ============================================================
export type I2Bucket = {
  value: string
  total: number
  started: number
  completed: number
}

export function useI2ContentCompletion(args: AnalyticsVizArgs) {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  return useAnalyticsFetcher<I2Bucket[]>(
    async () => {
      const response = await postAdminLogsTermsWithSubs({
        index: 'biz-events',
        query: composeQuery([serviceQuery]) || undefined,
        filters: serviceFilters.length > 0 ? serviceFilters : undefined,
        timeRange,
        groupBy: 'metadata.funnel_name',
        size: 10,
        subFilters: [
          {
            name: 'started',
            query:
              'event_name:funnel_step_completed AND metadata.step_name:(settings OR birth_info)',
          },
          { name: 'completed', query: 'event_name:funnel_goal_reached' },
        ],
      })
      return response.buckets.map((bucket) => ({
        value: bucket.value,
        total: bucket.total,
        started: bucket.sub.started ?? 0,
        completed: bucket.sub.completed ?? 0,
      }))
    },
    [timeRange, serviceFilters, serviceQuery, refreshNonce],
  )
}

// ============================================================
// 채널 viz — I6 결과 도달 후 공유 비율 (도넛)
// terms-with-subs(funnel_name) + sub-filter(goal/abandoned).
// shared = max(goal - abandoned, 0) 근사치.
// ============================================================
export type I6Bucket = {
  value: string
  goal: number
  abandoned: number
  shared: number
}

export function useI6ShareRate(args: AnalyticsVizArgs) {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  return useAnalyticsFetcher<I6Bucket[]>(
    async () => {
      const response = await postAdminLogsTermsWithSubs({
        index: 'biz-events',
        query: composeQuery([serviceQuery]) || undefined,
        filters: serviceFilters.length > 0 ? serviceFilters : undefined,
        timeRange,
        groupBy: 'metadata.funnel_name',
        size: 10,
        subFilters: [
          { name: 'goal', query: 'event_name:funnel_goal_reached' },
          { name: 'abandoned', query: 'event_name:result_share_abandoned' },
        ],
      })
      return response.buckets
        .map((bucket) => {
          const goal = bucket.sub.goal ?? 0
          const abandoned = bucket.sub.abandoned ?? 0
          const shared = Math.max(goal - abandoned, 0)
          return { value: bucket.value, goal, abandoned, shared }
        })
        .filter((bucket) => bucket.goal > 0)
    },
    [timeRange, serviceFilters, serviceQuery, refreshNonce],
  )
}

// ============================================================
// 컨텐츠/채널 timeline — I5 (funnel_name별), I10 (entry_type별)
// histogram + groupBy 차원. byField로 분해된 시계열.
// ============================================================
export type TimelineBucket = {
  ts: string
  total: number
  byField: Record<string, number>
}

export type TimelineData = {
  buckets: TimelineBucket[]
  interval: string
  // 차트가 그릴 series 라벨 — buckets에 등장한 모든 byField 키의 합집합.
  seriesKeys: string[]
}

const aggregateSeriesKeys = (buckets: TimelineBucket[]): string[] => {
  const set = new Set<string>()
  for (const bucket of buckets) {
    for (const key of Object.keys(bucket.byField)) set.add(key)
  }
  return Array.from(set).sort()
}

export function useI5EntryTimeline(args: AnalyticsVizArgs) {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  return useAnalyticsFetcher<TimelineData>(
    async () => {
      const response = await postAdminLogsHistogram({
        index: 'biz-events',
        query: composeQuery([serviceQuery, 'event_name:funnel_started']) || undefined,
        filters: serviceFilters.length > 0 ? serviceFilters : undefined,
        timeRange,
        groupBy: 'metadata.funnel_name',
      })
      const buckets: TimelineBucket[] = response.buckets.map((bucket) => ({
        ts: bucket.ts,
        total: bucket.total,
        byField: bucket.byField ?? {},
      }))
      return { buckets, interval: response.interval, seriesKeys: aggregateSeriesKeys(buckets) }
    },
    [timeRange, serviceFilters, serviceQuery, refreshNonce],
  )
}

export function useI10EntryChannelTimeline(args: AnalyticsVizArgs) {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  return useAnalyticsFetcher<TimelineData>(
    async () => {
      const response = await postAdminLogsHistogram({
        index: 'biz-events',
        query:
          composeQuery([serviceQuery, 'event_name:landing_source_detected']) || undefined,
        filters: serviceFilters.length > 0 ? serviceFilters : undefined,
        timeRange,
        groupBy: 'metadata.entry_type',
      })
      const buckets: TimelineBucket[] = response.buckets.map((bucket) => ({
        ts: bucket.ts,
        total: bucket.total,
        byField: bucket.byField ?? {},
      }))
      return { buckets, interval: response.interval, seriesKeys: aggregateSeriesKeys(buckets) }
    },
    [timeRange, serviceFilters, serviceQuery, refreshNonce],
  )
}

// ============================================================
// 흐름 viz — I3 단계별 이탈 깔때기 (faceted)
// composite-buckets(funnel_name, step_name) + subAggs(minStepIndex).
// ============================================================
export type I3Step = { name: string; count: number; minStepIndex: number }
export type I3Funnel = { name: string; steps: I3Step[] }

export function useI3FunnelAbandon(args: AnalyticsVizArgs) {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  return useAnalyticsFetcher<I3Funnel[]>(
    async () => {
      const response = await postAdminLogsCompositeBuckets({
        index: 'biz-events',
        query:
          composeQuery([serviceQuery, 'event_name:funnel_step_completed']) || undefined,
        filters: serviceFilters.length > 0 ? serviceFilters : undefined,
        timeRange,
        sources: ['metadata.funnel_name', 'metadata.step_name'],
        size: 200,
        subAggs: [{ name: 'minStepIndex', type: 'min', field: 'metadata.step_index' }],
      })
      // funnel_name별로 grouping.
      const grouped = new Map<string, I3Step[]>()
      for (const bucket of response.buckets) {
        const funnelName = bucket.keys['metadata.funnel_name'] ?? '(미지정)'
        const stepName = bucket.keys['metadata.step_name'] ?? '(미지정)'
        const minStepIndex = bucket.sub?.minStepIndex ?? 0
        const existing = grouped.get(funnelName) ?? []
        existing.push({ name: stepName, count: bucket.count, minStepIndex })
        grouped.set(funnelName, existing)
      }
      const funnels: I3Funnel[] = []
      for (const [name, steps] of grouped.entries()) {
        steps.sort((a, b) => a.minStepIndex - b.minStepIndex)
        funnels.push({ name, steps })
      }
      return funnels
    },
    [timeRange, serviceFilters, serviceQuery, refreshNonce],
  )
}

// ============================================================
// 체류 viz — I9 체험 공간 평균 체류 시간 (가로 막대)
// terms-with-metric(path) + avg(time_on_page_ms).
// 동적 segment(roomCode 등) 정규화는 클라이언트에서.
// ============================================================
export type I9Bucket = { path: string; avgSec: number; samples: number }

const PATH_NORMALIZE = (path: string): string =>
  path
    .replace(/\/relay-drawing\/[A-Z0-9]+/g, '/relay-drawing/:room')
    .replace(/\/flipbook\/lobby\/[A-Z0-9]+/g, '/flipbook/lobby/:room')
    .replace(/\/flipbook\/drawing\/[A-Z0-9]+/g, '/flipbook/drawing/:room')
    .replace(/\/flipbook\/result\/[A-Z0-9]+/g, '/flipbook/result/:room')
    .replace(/\/share\/[A-Za-z0-9_-]+/g, '/share/:token')

export function useI9DwellTime(args: AnalyticsVizArgs) {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  return useAnalyticsFetcher<I9Bucket[]>(
    async () => {
      const response = await postAdminLogsTermsWithMetric({
        index: 'biz-events',
        query:
          composeQuery([
            serviceQuery,
            'event_name:page_leave',
            'NOT path:*admin*',
          ]) || undefined,
        filters: serviceFilters.length > 0 ? serviceFilters : undefined,
        timeRange,
        groupBy: 'path',
        size: 30,
        metrics: [{ name: 'avgMs', type: 'avg', field: 'metadata.time_on_page_ms' }],
      })
      // path 정규화 후 가중 평균 합산.
      type Acc = { weightedSum: number; samples: number }
      const map = new Map<string, Acc>()
      for (const bucket of response.buckets) {
        const path = PATH_NORMALIZE(bucket.value)
        const avgMs = bucket.metrics.avgMs ?? 0
        const samples = bucket.total
        const existing = map.get(path) ?? { weightedSum: 0, samples: 0 }
        existing.weightedSum += avgMs * samples
        existing.samples += samples
        map.set(path, existing)
      }
      const buckets: I9Bucket[] = []
      for (const [path, acc] of map.entries()) {
        const avgSec = acc.samples > 0 ? acc.weightedSum / acc.samples / 1000 : 0
        if (avgSec <= 0) continue
        buckets.push({ path, avgSec, samples: acc.samples })
      }
      buckets.sort((a, b) => b.avgSec - a.avgSec)
      return buckets
    },
    [timeRange, serviceFilters, serviceQuery, refreshNonce],
  )
}

// ============================================================
// 체류 viz — I13 이탈 직전 평균 체류 시간 (3 카드)
// filtered-metrics(lobby/creation/result × avg metric).
// ============================================================
export type I13Phase = { name: string; label: string; avgSec: number; count: number; order: number }

export function useI13AbandonElapsed(args: AnalyticsVizArgs) {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  return useAnalyticsFetcher<I13Phase[]>(
    async () => {
      const response = await postAdminLogsFilteredMetrics({
        index: 'biz-events',
        query: composeQuery([serviceQuery]) || undefined,
        filters: serviceFilters.length > 0 ? serviceFilters : undefined,
        timeRange,
        groups: [
          {
            name: 'lobby',
            query: 'event_name:room_lobby_abandoned',
            metric: { type: 'avg', field: 'metadata.wait_time_ms' },
          },
          {
            name: 'creation',
            query: 'event_name:creation_abandoned',
            metric: { type: 'avg', field: 'metadata.elapsed_ms' },
          },
          {
            name: 'result',
            query: 'event_name:result_share_abandoned',
            metric: { type: 'avg', field: 'metadata.time_on_result_ms' },
          },
        ],
      })
      const phases: I13Phase[] = [
        {
          name: 'lobby',
          label: '로비 대기 후 이탈',
          avgSec: (response.groups.lobby?.metric ?? 0) / 1000,
          count: response.groups.lobby?.count ?? 0,
          order: 1,
        },
        {
          name: 'creation',
          label: '그리는 도중 이탈',
          avgSec: (response.groups.creation?.metric ?? 0) / 1000,
          count: response.groups.creation?.count ?? 0,
          order: 2,
        },
        {
          name: 'result',
          label: '결과 보고 이탈',
          avgSec: (response.groups.result?.metric ?? 0) / 1000,
          count: response.groups.result?.count ?? 0,
          order: 3,
        },
      ]
      return phases
    },
    [timeRange, serviceFilters, serviceQuery, refreshNonce],
  )
}
