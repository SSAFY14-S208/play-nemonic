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
}

export type AdminLogsFieldSummaryRequest = {
  index: AdminLogsIndex
  query?: string
  filters?: AdminLogsFilter[]
  timeRange: AdminLogsTimeRange
  fields: string[]
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
