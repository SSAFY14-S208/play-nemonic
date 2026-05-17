// 백오피스 로그 API DTO.
//
// 이 API는 ApiResponse 봉투 없이 평면 응답으로 온다(controller가
// ResponseEntity<LogsSearchResponse> 등을 직접 반환). 호출부는 apiUnwrap 없이
// adminApi.post<LogsSearchResponse>(...).
//
// 인덱스 화이트리스트와 시간 범위 30일 제한은 백엔드 LogsQueryBuilder가 강제한다.
// 400 응답은 ADMIN_LOGS_INVALID_* 코드의 평면 JSON, 401은 ADMIN_LOGS_UNAUTHORIZED.

export type AdminLogsIndex =
  | 'biz-events'
  | 'error-logs'
  | 'access-logs'
  | 'system-logs'
  | 'audit-logs'
  | 'all'

export type AdminLogsFilter = {
  field: string
  value: string | number | boolean
  negate?: boolean
}

export type AdminLogsTimeRange = {
  from: string
  to: string
}

export type AdminLogsSearchRequest = {
  index: AdminLogsIndex
  query?: string
  filters?: AdminLogsFilter[]
  timeRange: AdminLogsTimeRange
  size?: number
  searchAfter?: [string, string]
}

export type AdminLogsHistogramRequest = {
  index: AdminLogsIndex
  query?: string
  filters?: AdminLogsFilter[]
  timeRange: AdminLogsTimeRange
  // BE 확장: 옵션. 미지정 시 byLevel만, 지정 시 byField 동반.
  groupBy?: string
}

export type AdminLogsFieldSummaryRequest = {
  index: AdminLogsIndex
  query?: string
  filters?: AdminLogsFilter[]
  timeRange: AdminLogsTimeRange
  fields: string[]
  // BE 확장: per-request size (default 10, max 100). 모든 fields에 동일 size 적용.
  size?: number
}

export type AdminLogsSearchHit = {
  id: string
  index: string
  source: Record<string, unknown>
}

export type AdminLogsSearchResponse = {
  total: number
  tookMs: number
  hits: AdminLogsSearchHit[]
  nextSearchAfter: [string, string] | null
}

export type AdminLogsHistogramBucket = {
  ts: string
  total: number
  byLevel: Record<string, number>
  // BE 확장: groupBy 지정 시에만 동반.
  byField?: Record<string, number>
}

export type AdminLogsHistogramResponse = {
  interval: string
  buckets: AdminLogsHistogramBucket[]
}

export type AdminLogsFieldSummaryItem = {
  value: string
  count: number
}

export type AdminLogsFieldSummaryResponse = {
  fields: Record<string, AdminLogsFieldSummaryItem[]>
}

// 백엔드 평면 에러 응답.
export type AdminLogsErrorResponse = {
  success: false
  code: string
  message: string
  timestamp: string
}

// 신규: POST /admin/logs/terms-with-subs
export type AdminLogsSubFilter = {
  name: string
  query: string
}

export type AdminLogsTermsWithSubsRequest = {
  index: AdminLogsIndex
  query?: string
  filters?: AdminLogsFilter[]
  timeRange: AdminLogsTimeRange
  groupBy: string
  size?: number
  subFilters: AdminLogsSubFilter[]
}

export type AdminLogsTermsWithSubsBucket = {
  value: string
  total: number
  sub: Record<string, number>
}

export type AdminLogsTermsWithSubsResponse = {
  buckets: AdminLogsTermsWithSubsBucket[]
}

// 신규: POST /admin/logs/terms-with-metric
export type AdminLogsMetricType = 'avg' | 'sum' | 'max' | 'min'

export type AdminLogsMetricSpec = {
  name: string
  type: AdminLogsMetricType
  field: string
}

export type AdminLogsTermsWithMetricRequest = {
  index: AdminLogsIndex
  query?: string
  filters?: AdminLogsFilter[]
  timeRange: AdminLogsTimeRange
  groupBy: string
  size?: number
  metrics: AdminLogsMetricSpec[]
}

export type AdminLogsTermsWithMetricBucket = {
  value: string
  total: number
  metrics: Record<string, number>
}

export type AdminLogsTermsWithMetricResponse = {
  buckets: AdminLogsTermsWithMetricBucket[]
}

// 신규: POST /admin/logs/composite-buckets
export type AdminLogsCompositeAfterKey = Record<string, string | null>

export type AdminLogsCompositeSubAgg = {
  name: string
  type: AdminLogsMetricType
  field: string
}

export type AdminLogsCompositeBucketsRequest = {
  index: AdminLogsIndex
  query?: string
  filters?: AdminLogsFilter[]
  timeRange: AdminLogsTimeRange
  sources: [string, string]
  size?: number
  after?: AdminLogsCompositeAfterKey | null
  subAggs?: AdminLogsCompositeSubAgg[]
}

export type AdminLogsCompositeBucket = {
  keys: Record<string, string | null>
  count: number
  sub?: Record<string, number>
}

export type AdminLogsCompositeBucketsResponse = {
  buckets: AdminLogsCompositeBucket[]
  afterKey: AdminLogsCompositeAfterKey | null
}

// 신규: POST /admin/logs/filtered-metrics
export type AdminLogsFilteredMetricGroup = {
  name: string
  query: string
  metric?: { type: AdminLogsMetricType; field: string }
}

export type AdminLogsFilteredMetricsRequest = {
  index: AdminLogsIndex
  query?: string
  filters?: AdminLogsFilter[]
  timeRange: AdminLogsTimeRange
  groups: AdminLogsFilteredMetricGroup[]
}

export type AdminLogsFilteredMetricsGroupResponse = {
  count: number
  metric?: number
}

export type AdminLogsFilteredMetricsResponse = {
  groups: Record<string, AdminLogsFilteredMetricsGroupResponse>
}

// 신규: POST /admin/logs/distinct-count
export type AdminLogsDistinctCountField = 'uuid' | 'session_id' | 'trace_id'

export type AdminLogsDistinctCountRequest = {
  index: AdminLogsIndex
  query?: string
  filters?: AdminLogsFilter[]
  timeRange: AdminLogsTimeRange
  field: AdminLogsDistinctCountField
  precisionThreshold?: number
}

export type AdminLogsDistinctCountResponse = {
  value: number
}
