'use client'

import { useEffect, useState } from 'react'

import { postAdminLogsDistinctCount, postAdminLogsSearch } from '@/shared/apis'
import type { AdminLogsFilter, AdminLogsTimeRange } from '@/shared/types'

import type { AnalyticsKpiState, I1KpiData, I11KpiData, I12KpiData } from '../types'

// I1·I11·I12 KPI 데이터 fetch.
//
// 현 백엔드 API는 `search size:0`만 노출 — total 카운트만 받을 수 있다. KPI 카드는
// 각 셀이 독립된 count라 search를 병렬 호출해 채운다. histogram/field-summary는 다른
// 차원으로 분해할 때 쓰지만 KPI는 단순 카운트라 search만으로 충분.
//
// 4xx (잘못된 시간 범위 등) 응답은 백엔드 평면 에러로 떨어진다 — adminApi가 throw하므로
// catch에서 message 추출. 401은 인터셉터가 reissue 시도 후 재시도, 실패하면 throw.

type KpiFetcherArgs = {
  timeRange: AdminLogsTimeRange
  serviceFilters: AdminLogsFilter[]
  serviceQuery: string | undefined
  refreshNonce: number
}

const composeQuery = (parts: Array<string | undefined>): string => {
  const segments = parts.filter((segment): segment is string => Boolean(segment && segment.trim()))
  return segments.join(' AND ')
}

const fetchCount = async (
  query: string,
  timeRange: AdminLogsTimeRange,
  serviceFilters: AdminLogsFilter[],
): Promise<number> => {
  const response = await postAdminLogsSearch({
    index: 'biz-events',
    query: query || undefined,
    filters: serviceFilters.length > 0 ? serviceFilters : undefined,
    timeRange,
    size: 1,
  })
  return response.total
}

const errorMessage = (caughtError: unknown): string => {
  if (caughtError instanceof Error && caughtError.message) {
    return caughtError.message
  }
  return '데이터를 불러오지 못했어요'
}

export function useI1Kpi(args: KpiFetcherArgs): AnalyticsKpiState<I1KpiData> {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  const [state, setState] = useState<AnalyticsKpiState<I1KpiData>>({
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
        const [activeSessions, funnelStarted, funnelGoalReached, funnelAbandoned] =
          await Promise.all([
            fetchCount(
              composeQuery([serviceQuery, 'event_name:client_alive']),
              timeRange,
              serviceFilters,
            ),
            fetchCount(
              composeQuery([serviceQuery, 'event_name:funnel_started']),
              timeRange,
              serviceFilters,
            ),
            fetchCount(
              composeQuery([serviceQuery, 'event_name:funnel_goal_reached']),
              timeRange,
              serviceFilters,
            ),
            fetchCount(
              composeQuery([
                serviceQuery,
                'event_name:(funnel_abandoned OR room_lobby_abandoned OR creation_abandoned OR result_share_abandoned)',
              ]),
              timeRange,
              serviceFilters,
            ),
          ])
        if (cancelled) return
        setState({
          data: { activeSessions, funnelStarted, funnelGoalReached, funnelAbandoned },
          isLoading: false,
          errorMessage: null,
        })
      } catch (caughtError) {
        if (cancelled) return
        setState({
          data: null,
          isLoading: false,
          errorMessage: errorMessage(caughtError),
        })
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [timeRange, serviceFilters, serviceQuery, refreshNonce])

  return state
}

export function useI11Kpi(args: KpiFetcherArgs): AnalyticsKpiState<I11KpiData> {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  const [state, setState] = useState<AnalyticsKpiState<I11KpiData>>({
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
        // BE 확장(distinct-count) 머지 이후: cardinality(uuid)로 정확값 사용.
        // visitors = 기간 내 distinct uuid, completedVisitors = funnel_goal_reached
        // 이벤트를 발생시킨 distinct uuid.
        const baseQuery = composeQuery([serviceQuery]) || undefined
        const completedQuery =
          composeQuery([serviceQuery, 'event_name:funnel_goal_reached']) || undefined
        const filters = serviceFilters.length > 0 ? serviceFilters : undefined
        const [visitorsResponse, completedResponse] = await Promise.all([
          postAdminLogsDistinctCount({
            index: 'biz-events',
            query: baseQuery,
            filters,
            timeRange,
            field: 'uuid',
          }),
          postAdminLogsDistinctCount({
            index: 'biz-events',
            query: completedQuery,
            filters,
            timeRange,
            field: 'uuid',
          }),
        ])
        if (cancelled) return
        const visitors = visitorsResponse.value
        const completedVisitors = completedResponse.value
        const completionRate = visitors > 0 ? (completedVisitors / visitors) * 100 : 0
        setState({
          data: { visitors, completedVisitors, completionRate },
          isLoading: false,
          errorMessage: null,
        })
      } catch (caughtError) {
        if (cancelled) return
        setState({
          data: null,
          isLoading: false,
          errorMessage: errorMessage(caughtError),
        })
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [timeRange, serviceFilters, serviceQuery, refreshNonce])

  return state
}

export function useI12Kpi(args: KpiFetcherArgs): AnalyticsKpiState<I12KpiData> {
  const { timeRange, serviceFilters, serviceQuery, refreshNonce } = args
  const [state, setState] = useState<AnalyticsKpiState<I12KpiData>>({
    data: null,
    isLoading: true,
    errorMessage: null,
  })

  useEffect(() => {
    let cancelled = false

    // 결과 화면 path glob. 백엔드 LogsQueryBuilder는 `\` 포함 시 400 거부하므로
    // OSD 원본의 `\/` 이스케이프 대신 wildcard substring 매칭으로 우회.
    // result/share/fortune 키워드는 결과 도달 화면 path에만 등장하는 substring이라
    // 의도치 않은 over-match는 무시 가능 (admin 라우트는 별도 filter로 제외됨).
    const RESULT_PATH_QUERY = '(path:*result* OR path:*share* OR path:*fortune*)'
    const baseQuery = composeQuery([
      serviceQuery,
      'event_name:page_leave',
      RESULT_PATH_QUERY,
    ])

    const run = async () => {
      if (!cancelled) {
        setState((previous) => ({ ...previous, isLoading: true, errorMessage: null }))
      }
      try {
        const [bounceUnder5s, short5to30s, normal30to60s, immersedOver60s] =
          await Promise.all([
            fetchCount(
              composeQuery([baseQuery, 'metadata.time_on_page_ms:[0 TO 4999]']),
              timeRange,
              serviceFilters,
            ),
            fetchCount(
              composeQuery([baseQuery, 'metadata.time_on_page_ms:[5000 TO 29999]']),
              timeRange,
              serviceFilters,
            ),
            fetchCount(
              composeQuery([baseQuery, 'metadata.time_on_page_ms:[30000 TO 59999]']),
              timeRange,
              serviceFilters,
            ),
            fetchCount(
              composeQuery([baseQuery, 'metadata.time_on_page_ms:[60000 TO *]']),
              timeRange,
              serviceFilters,
            ),
          ])
        if (cancelled) return
        setState({
          data: { bounceUnder5s, short5to30s, normal30to60s, immersedOver60s },
          isLoading: false,
          errorMessage: null,
        })
      } catch (caughtError) {
        if (cancelled) return
        setState({
          data: null,
          isLoading: false,
          errorMessage: errorMessage(caughtError),
        })
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [timeRange, serviceFilters, serviceQuery, refreshNonce])

  return state
}
