import { adminApi } from '@/shared/libs'
import type {
  AdminMetricsQueryRangeRequest,
  AdminMetricsQueryRangeResponse,
  AdminMetricsQueryRequest,
  AdminMetricsQueryResponse,
} from '@/shared/types'

// 백오피스 Prometheus 메트릭 프록시 API — ApiResponse 봉투 없이 평면 응답.
// adminApi 인터셉터가 Authorization 헤더를 자동 주입한다.
//
// FE는 PromQL을 직접 보내지 않고 화이트리스트 template id + params만 보낸다.

// POST /admin/metrics/query — instant value (현재 값 1개)
export const postAdminMetricsQuery = (payload: AdminMetricsQueryRequest) =>
  adminApi.post<AdminMetricsQueryResponse>('admin/metrics/query', payload)

// POST /admin/metrics/query-range — time series
export const postAdminMetricsQueryRange = (payload: AdminMetricsQueryRangeRequest) =>
  adminApi.post<AdminMetricsQueryRangeResponse>('admin/metrics/query-range', payload)
