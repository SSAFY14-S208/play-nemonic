import { adminApi } from '@/shared/libs'
import type {
  AdminLogsCompositeBucketsRequest,
  AdminLogsCompositeBucketsResponse,
  AdminLogsDistinctCountRequest,
  AdminLogsDistinctCountResponse,
  AdminLogsFieldSummaryRequest,
  AdminLogsFieldSummaryResponse,
  AdminLogsFilteredMetricsRequest,
  AdminLogsFilteredMetricsResponse,
  AdminLogsHistogramRequest,
  AdminLogsHistogramResponse,
  AdminLogsSearchRequest,
  AdminLogsSearchResponse,
  AdminLogsTermsWithMetricRequest,
  AdminLogsTermsWithMetricResponse,
  AdminLogsTermsWithSubsRequest,
  AdminLogsTermsWithSubsResponse,
} from '@/shared/types'

// 백오피스 로그 API — ApiResponse 봉투 없이 평면 응답.
// adminApi 인터셉터가 Authorization 헤더를 자동 주입한다.

// POST /admin/logs/search — 로그 검색
export const postAdminLogsSearch = (payload: AdminLogsSearchRequest) =>
  adminApi.post<AdminLogsSearchResponse>('admin/logs/search', payload)

// POST /admin/logs/histogram — 시계열 버킷 (BE 확장: groupBy 옵션 추가)
export const postAdminLogsHistogram = (payload: AdminLogsHistogramRequest) =>
  adminApi.post<AdminLogsHistogramResponse>('admin/logs/histogram', payload)

// POST /admin/logs/field-summary — 필드 top 값 (BE 확장: size 옵션 추가)
export const postAdminLogsFieldSummary = (payload: AdminLogsFieldSummaryRequest) =>
  adminApi.post<AdminLogsFieldSummaryResponse>('admin/logs/field-summary', payload)

// POST /admin/logs/terms-with-subs — terms grouping + 다중 sub-filter count
export const postAdminLogsTermsWithSubs = (payload: AdminLogsTermsWithSubsRequest) =>
  adminApi.post<AdminLogsTermsWithSubsResponse>('admin/logs/terms-with-subs', payload)

// POST /admin/logs/terms-with-metric — terms grouping + avg/sum/max/min metric
export const postAdminLogsTermsWithMetric = (payload: AdminLogsTermsWithMetricRequest) =>
  adminApi.post<AdminLogsTermsWithMetricResponse>('admin/logs/terms-with-metric', payload)

// POST /admin/logs/composite-buckets — composite 2-key buckets + optional sub-agg
export const postAdminLogsCompositeBuckets = (payload: AdminLogsCompositeBucketsRequest) =>
  adminApi.post<AdminLogsCompositeBucketsResponse>('admin/logs/composite-buckets', payload)

// POST /admin/logs/filtered-metrics — 다중 named filter + 그룹별 metric
export const postAdminLogsFilteredMetrics = (payload: AdminLogsFilteredMetricsRequest) =>
  adminApi.post<AdminLogsFilteredMetricsResponse>('admin/logs/filtered-metrics', payload)

// POST /admin/logs/distinct-count — cardinality (uuid 등 distinct 개수)
export const postAdminLogsDistinctCount = (payload: AdminLogsDistinctCountRequest) =>
  adminApi.post<AdminLogsDistinctCountResponse>('admin/logs/distinct-count', payload)
