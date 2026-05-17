'use client'

import { useEffect, useState } from 'react'

import {
  postAdminLogsDistinctCount,
  postAdminLogsFieldSummary,
  postAdminLogsHistogram,
  postAdminMetricsQuery,
  postAdminMetricsQueryRange,
} from '@/shared/apis'
import type {
  AdminLogsTimeRange,
  AdminMetricsQueryRangeResponse,
  AdminMetricsQueryResponse,
  AdminMetricsSeriesRange,
  AdminMetricsTemplateId,
  AdminMetricsTimeRange,
} from '@/shared/types'

import { rateWindowFor, stepFor } from '../constants'
import type {
  MetricsAsyncState,
  MetricsTimeRangePresetKey,
} from '../types'

// 23 panel의 데이터 fetch 훅. Hybrid 데이터 소스:
//   - Prometheus 프록시 (`/admin/metrics/*`) : 시스템 메트릭, HTTP, host 리소스
//   - 기존 OSD logs API (`/admin/logs/*`)    : 사용자 행동 (uuid, session, funnel)
//
// 각 훅은 동일 args (timeRange, preset, refreshNonce)를 받아 의존성으로 재호출.
// 응답을 panel 컴포넌트가 쓰기 좋은 shape으로 가공해 반환.

export type MetricsVizArgs = {
  timeRange: AdminMetricsTimeRange
  preset: MetricsTimeRangePresetKey
  refreshNonce: number
}

const errorMessage = (caughtError: unknown): string => {
  if (caughtError instanceof Error && caughtError.message) return caughtError.message
  return '데이터를 불러오지 못했어요'
}

// 공통 fetch 패턴 — loading/error 상태 머신.
function useMetricsFetcher<T>(
  fetcher: () => Promise<T>,
  deps: ReadonlyArray<unknown>,
): MetricsAsyncState<T> {
  const [state, setState] = useState<MetricsAsyncState<T>>({
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

// AdminLogsTimeRange와 AdminMetricsTimeRange는 shape이 같아 그대로 캐스팅.
const asLogsTimeRange = (range: AdminMetricsTimeRange): AdminLogsTimeRange => ({
  from: range.from,
  to: range.to,
})

// Prometheus 응답을 recharts 친화적 평면 row 배열로 변환.
// labelKey로 각 series를 column key로 사용.
export type TimelineRow = {
  ts: string
  [seriesKey: string]: string | number | null
}

export const seriesRangeToRows = (
  series: AdminMetricsSeriesRange[],
  labelKey: (labels: Record<string, string>, index: number) => string,
): { rows: TimelineRow[]; keys: string[] } => {
  if (series.length === 0) return { rows: [], keys: [] }
  const keys = series.map((seriesItem, index) => labelKey(seriesItem.labels, index))
  // ts 별로 row를 합치기. ts 집합을 모은 뒤 각 series의 ts→value 맵으로 채움.
  const tsToRow = new Map<string, TimelineRow>()
  series.forEach((seriesItem, seriesIndex) => {
    const key = keys[seriesIndex]
    for (const sample of seriesItem.samples) {
      const existing = tsToRow.get(sample.ts) ?? { ts: sample.ts }
      existing[key] = sample.value
      tsToRow.set(sample.ts, existing)
    }
  })
  const rows = Array.from(tsToRow.values()).sort((a, b) => (a.ts < b.ts ? -1 : 1))
  return { rows, keys }
}

// ============================================================
// Row 1 — 서비스 상태 (up)
// ============================================================
export type ServiceUpResult = {
  job: string
  up: boolean
  // 응답이 비어 있으면 unknown (Prometheus가 그 job을 모르는 경우).
  unknown: boolean
}

export function useServiceUpStatus(args: MetricsVizArgs, job: string): MetricsAsyncState<ServiceUpResult> {
  const { refreshNonce } = args
  return useMetricsFetcher<ServiceUpResult>(
    async () => {
      const response = await postAdminMetricsQuery({
        template: 'service_up_status',
        params: { job },
      })
      const first = response.series[0]
      if (!first) return { job, up: false, unknown: true }
      return { job, up: first.value === 1, unknown: false }
    },
    [job, refreshNonce],
  )
}

// ============================================================
// Row 2 — Nginx 요청 처리율
// ============================================================
type RangeArgs = MetricsVizArgs & { template: AdminMetricsTemplateId; params?: Record<string, string> }

function useMetricsRange(args: RangeArgs): MetricsAsyncState<AdminMetricsQueryRangeResponse> {
  const { timeRange, preset, refreshNonce, template, params } = args
  return useMetricsFetcher<AdminMetricsQueryRangeResponse>(
    async () =>
      postAdminMetricsQueryRange({
        template,
        params,
        timeRange,
        step: stepFor(preset),
      }),
    [timeRange, preset, refreshNonce, template, JSON.stringify(params)],
  )
}

function useMetricsInstant(args: {
  refreshNonce: number
  template: AdminMetricsTemplateId
  params?: Record<string, string>
}): MetricsAsyncState<AdminMetricsQueryResponse> {
  const { refreshNonce, template, params } = args
  return useMetricsFetcher<AdminMetricsQueryResponse>(
    async () => postAdminMetricsQuery({ template, params }),
    [refreshNonce, template, JSON.stringify(params)],
  )
}

export const useNginxRequestRate = (args: MetricsVizArgs) =>
  useMetricsRange({
    ...args,
    template: 'nginx_request_rate',
    params: { window: rateWindowFor(args.preset) },
  })

export const useNginxActiveConnections = (args: MetricsVizArgs) =>
  useMetricsRange({
    ...args,
    template: 'nginx_active_connections',
  })

export const useSpringHttpRps = (args: MetricsVizArgs) =>
  useMetricsRange({
    ...args,
    template: 'spring_http_rps_by_service',
    params: { window: rateWindowFor(args.preset) },
  })

// p50/p95/p99 3 시리즈 — 3번 호출 후 합성.
export type LatencyQuantileData = {
  rows: TimelineRow[]
  keys: string[]
}

export function useSpringHttpLatency(args: MetricsVizArgs): MetricsAsyncState<LatencyQuantileData> {
  const { timeRange, preset, refreshNonce } = args
  return useMetricsFetcher<LatencyQuantileData>(
    async () => {
      const quantiles: Array<'0.5' | '0.95' | '0.99'> = ['0.5', '0.95', '0.99']
      const responses = await Promise.all(
        quantiles.map((quantile) =>
          postAdminMetricsQueryRange({
            template: 'spring_http_latency_quantile',
            params: { quantile, window: rateWindowFor(preset) },
            timeRange,
            step: stepFor(preset),
          }),
        ),
      )
      // 시리즈 별 quantile 라벨 부여 후 ts merge.
      const labeled: AdminMetricsSeriesRange[] = responses.flatMap((response, index) =>
        response.series.map((seriesItem) => ({
          labels: { ...seriesItem.labels, quantile: quantiles[index] },
          samples: seriesItem.samples,
        })),
      )
      return seriesRangeToRows(labeled, (labels) => labels.quantile ?? '?')
    },
    [timeRange, preset, refreshNonce],
  )
}

export const useSpringHttpErrorRatio = (args: MetricsVizArgs) =>
  useMetricsRange({
    ...args,
    template: 'spring_http_error_ratio',
    params: { window: rateWindowFor(args.preset) },
  })

// ============================================================
// Row 3 — 사용자 행동 (Hybrid: OSD distinct-count + Prometheus + OSD histogram)
// ============================================================
export const useActiveUuidCount = (args: MetricsVizArgs) =>
  useMetricsFetcher<number>(
    async () => {
      const response = await postAdminLogsDistinctCount({
        index: 'biz-events',
        query: 'service:client-web',
        timeRange: asLogsTimeRange(args.timeRange),
        field: 'uuid',
      })
      return response.value
    },
    [args.timeRange, args.refreshNonce],
  )

export const useActiveSessionCount = (args: MetricsVizArgs) =>
  useMetricsFetcher<number>(
    async () => {
      const response = await postAdminLogsDistinctCount({
        index: 'biz-events',
        query: 'service:client-web',
        timeRange: asLogsTimeRange(args.timeRange),
        field: 'session_id',
      })
      return response.value
    },
    [args.timeRange, args.refreshNonce],
  )

export const useWebSocketSessions = (args: MetricsVizArgs) =>
  useMetricsInstant({
    refreshNonce: args.refreshNonce,
    template: 'ws_active_sessions',
  })

export function useWebSocketRate(args: MetricsVizArgs): MetricsAsyncState<LatencyQuantileData> {
  const { timeRange, preset, refreshNonce } = args
  return useMetricsFetcher<LatencyQuantileData>(
    async () => {
      const [connect, disconnect] = await Promise.all([
        postAdminMetricsQueryRange({
          template: 'ws_connect_rate',
          params: { window: rateWindowFor(preset) },
          timeRange,
          step: stepFor(preset),
        }),
        postAdminMetricsQueryRange({
          template: 'ws_disconnect_rate',
          params: { window: rateWindowFor(preset) },
          timeRange,
          step: stepFor(preset),
        }),
      ])
      const labeled: AdminMetricsSeriesRange[] = [
        ...connect.series.map((seriesItem) => ({
          labels: { ...seriesItem.labels, direction: 'connect' },
          samples: seriesItem.samples,
        })),
        ...disconnect.series.map((seriesItem) => ({
          labels: { ...seriesItem.labels, direction: 'disconnect' },
          samples: seriesItem.samples,
        })),
      ]
      return seriesRangeToRows(labeled, (labels) => labels.direction ?? '?')
    },
    [timeRange, preset, refreshNonce],
  )
}

// 시간대별 entry_type 유입. OSD histogram + groupBy.
export type HistogramTimelineData = {
  rows: TimelineRow[]
  keys: string[]
  interval: string
}

const histogramToTimeline = (
  buckets: Array<{ ts: string; byField?: Record<string, number> }>,
): { rows: TimelineRow[]; keys: string[] } => {
  const keySet = new Set<string>()
  const rows: TimelineRow[] = buckets.map((bucket) => {
    const row: TimelineRow = { ts: bucket.ts }
    for (const [fieldKey, count] of Object.entries(bucket.byField ?? {})) {
      keySet.add(fieldKey)
      row[fieldKey] = count
    }
    return row
  })
  return { rows, keys: Array.from(keySet).sort() }
}

export const useEntryChannelTimeline = (args: MetricsVizArgs): MetricsAsyncState<HistogramTimelineData> =>
  useMetricsFetcher<HistogramTimelineData>(
    async () => {
      const response = await postAdminLogsHistogram({
        index: 'biz-events',
        query: 'service:client-web AND event_name:landing_source_detected',
        timeRange: asLogsTimeRange(args.timeRange),
        groupBy: 'metadata.entry_type',
      })
      const { rows, keys } = histogramToTimeline(response.buckets)
      return { rows, keys, interval: response.interval }
    },
    [args.timeRange, args.refreshNonce],
  )

export const useFunnelEventsTimeline = (args: MetricsVizArgs): MetricsAsyncState<HistogramTimelineData> =>
  useMetricsFetcher<HistogramTimelineData>(
    async () => {
      const response = await postAdminLogsHistogram({
        index: 'biz-events',
        query:
          'service:client-web AND event_name:(funnel_started OR funnel_goal_reached OR funnel_abandoned)',
        timeRange: asLogsTimeRange(args.timeRange),
        groupBy: 'event_name',
      })
      const { rows, keys } = histogramToTimeline(response.buckets)
      return { rows, keys, interval: response.interval }
    },
    [args.timeRange, args.refreshNonce],
  )

export const useFlipbookCompletionTimeline = (args: MetricsVizArgs): MetricsAsyncState<HistogramTimelineData> =>
  useMetricsFetcher<HistogramTimelineData>(
    async () => {
      const response = await postAdminLogsHistogram({
        index: 'biz-events',
        query:
          'service:client-web AND metadata.funnel_name:flipbook_room_creation AND event_name:(funnel_step_completed OR funnel_goal_reached OR funnel_abandoned)',
        timeRange: asLogsTimeRange(args.timeRange),
        groupBy: 'event_name',
      })
      const { rows, keys } = histogramToTimeline(response.buckets)
      return { rows, keys, interval: response.interval }
    },
    [args.timeRange, args.refreshNonce],
  )

// 콘텐츠 타입별 활성 사용자 (uuid distinct가 비싸므로 session_id 단위 histogram 사용).
export const useActiveByContentTypeTimeline = (args: MetricsVizArgs): MetricsAsyncState<HistogramTimelineData> =>
  useMetricsFetcher<HistogramTimelineData>(
    async () => {
      const response = await postAdminLogsHistogram({
        index: 'biz-events',
        query: 'service:client-web AND event_name:funnel_started',
        timeRange: asLogsTimeRange(args.timeRange),
        groupBy: 'metadata.funnel_name',
      })
      const { rows, keys } = histogramToTimeline(response.buckets)
      return { rows, keys, interval: response.interval }
    },
    [args.timeRange, args.refreshNonce],
  )

// ============================================================
// Row 4 — 콘텐츠 활성 + 주요 이벤트 발생률
// ============================================================
export const useActiveContent = (args: MetricsVizArgs) =>
  useMetricsRange({
    ...args,
    template: 'content_active_rooms_by_type',
  })

export type TopEventBucket = { value: string; count: number }

export const useTopEvents = (args: MetricsVizArgs): MetricsAsyncState<TopEventBucket[]> =>
  useMetricsFetcher<TopEventBucket[]>(
    async () => {
      const response = await postAdminLogsFieldSummary({
        index: 'biz-events',
        query: 'service:client-web',
        timeRange: asLogsTimeRange(args.timeRange),
        fields: ['event_name'],
        size: 10,
      })
      return response.fields.event_name ?? []
    },
    [args.timeRange, args.refreshNonce],
  )

// ============================================================
// Row 5 — host 리소스
// ============================================================
export const useHostCpu = (args: MetricsVizArgs) =>
  useMetricsRange({
    ...args,
    template: 'host_cpu_usage_percent',
    params: { window: rateWindowFor(args.preset) },
  })

export const useHostMemory = (args: MetricsVizArgs) =>
  useMetricsInstant({
    refreshNonce: args.refreshNonce,
    template: 'host_memory_usage_percent',
  })

export const useHostDisk = (args: MetricsVizArgs) =>
  useMetricsInstant({
    refreshNonce: args.refreshNonce,
    template: 'host_disk_usage_percent',
  })

export type NetworkIoData = {
  rows: TimelineRow[]
  keys: string[]
}

export function useHostNetworkIo(args: MetricsVizArgs): MetricsAsyncState<NetworkIoData> {
  const { timeRange, preset, refreshNonce } = args
  return useMetricsFetcher<NetworkIoData>(
    async () => {
      const [receive, transmit] = await Promise.all([
        postAdminMetricsQueryRange({
          template: 'host_network_receive_bytes',
          params: { window: rateWindowFor(preset) },
          timeRange,
          step: stepFor(preset),
        }),
        postAdminMetricsQueryRange({
          template: 'host_network_transmit_bytes',
          params: { window: rateWindowFor(preset) },
          timeRange,
          step: stepFor(preset),
        }),
      ])
      const labeled: AdminMetricsSeriesRange[] = [
        ...receive.series.map((seriesItem) => ({
          labels: { ...seriesItem.labels, direction: 'in' },
          samples: seriesItem.samples,
        })),
        ...transmit.series.map((seriesItem) => ({
          labels: { ...seriesItem.labels, direction: 'out' },
          samples: seriesItem.samples,
        })),
      ]
      return seriesRangeToRows(labeled, (labels) => labels.direction ?? '?')
    },
    [timeRange, preset, refreshNonce],
  )
}
