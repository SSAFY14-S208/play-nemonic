// 백오피스 Prometheus 메트릭 프록시 API DTO.
//
// BE는 FE가 PromQL을 직접 보내지 못하도록 화이트리스트 템플릿 id + params 방식을
// 채택. FE는 template id와 params만 알면 되고 실제 PromQL은 BE가 빌드한다.
//
// 응답은 ApiResponse 봉투 없이 평면 (logs API와 동일 컨벤션). 401/400/502/504도
// 평면 에러(`{success:false, code, message, timestamp}`).

export type AdminMetricsTimeRange = {
  from: string
  to: string
}

// 알려진 템플릿 id — BE의 MetricsTemplateRegistry와 일치해야 함.
export type AdminMetricsTemplateId =
  // Row 1
  | 'service_up_status'
  // Row 2
  | 'nginx_request_rate'
  | 'nginx_active_connections'
  | 'spring_http_rps_by_service'
  | 'spring_http_latency_quantile'
  | 'spring_http_error_ratio'
  // Row 3 (Prometheus 부분)
  | 'ws_active_sessions'
  | 'ws_connect_rate'
  | 'ws_disconnect_rate'
  // Row 4
  | 'content_active_rooms_by_type'
  // Row 5
  | 'host_cpu_usage_percent'
  | 'host_memory_usage_percent'
  | 'host_disk_usage_percent'
  | 'host_network_receive_bytes'
  | 'host_network_transmit_bytes'

export type AdminMetricsQueryRequest = {
  template: AdminMetricsTemplateId
  params?: Record<string, string>
}

export type AdminMetricsQueryRangeRequest = {
  template: AdminMetricsTemplateId
  params?: Record<string, string>
  timeRange: AdminMetricsTimeRange
  // duration string (e.g. "30s", "1m"). BE 검증: 5s~1h.
  step: string
}

export type AdminMetricsSeriesInstant = {
  labels: Record<string, string>
  // Prometheus의 NaN/+Inf는 null로 변환됨.
  value: number | null
}

export type AdminMetricsSample = {
  ts: string
  value: number | null
}

export type AdminMetricsSeriesRange = {
  labels: Record<string, string>
  samples: AdminMetricsSample[]
}

export type AdminMetricsQueryResponse = {
  resultType: 'vector'
  series: AdminMetricsSeriesInstant[]
}

export type AdminMetricsQueryRangeResponse = {
  resultType: 'matrix'
  interval: string
  series: AdminMetricsSeriesRange[]
}

// 백엔드 평면 에러 응답.
export type AdminMetricsErrorResponse = {
  success: false
  code: string
  message: string
  timestamp: string
}
