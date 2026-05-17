import type { AdminLogsFilter } from '@/shared/types'

// 시간 범위 프리셋 키 — 1h/24h/7d/30d/커스텀.
export type LogsTimeRangePresetKey =
  | 'last-1h'
  | 'last-24h'
  | 'last-7d'
  | 'last-30d'
  | 'custom'

// 필터 바 상태 — 시간 범위 + service 멀티 셀렉트.
export type AnalyticsFiltersState = {
  preset: LogsTimeRangePresetKey
  // preset이 'custom'일 때만 의미 있음. ISO 문자열.
  customFrom: string
  customTo: string
  // service 멀티 셀렉트. 빈 배열이면 전체.
  services: string[]
  // 자동 갱신 토글.
  autoRefresh: boolean
  // refresh 트리거용 단조 증가 카운터 — KPI 훅이 의존성으로 받아 재호출.
  refreshNonce: number
}

// 13개 viz 식별자.
export type VizId =
  | 'I1'
  | 'I2'
  | 'I3'
  | 'I4'
  | 'I5'
  | 'I6'
  | 'I7'
  | 'I8'
  | 'I9'
  | 'I10'
  | 'I11'
  | 'I12'
  | 'I13'

export type VizSection =
  | 'overview'
  | 'channel'
  | 'content'
  | 'flow'
  | 'retention'

// 페이지 골격에서 어떤 컴포넌트를 그릴지 결정.
// 'live' — 실제 데이터 차트 / 'pending' — 백엔드 확장 대기 placeholder.
export type VizStatus = 'live' | 'pending'

export type ViizMeta = {
  id: VizId
  section: VizSection
  title: string
  subtitle: string
  status: VizStatus
}

// 드릴다운 패널 상태 — 차트 요소 클릭 시 set.
export type AnalyticsDrillDownState = {
  vizId: VizId
  chartLabel: string
  // 해당 차트가 적용한 dimension 필터. 패널의 search/histogram에 그대로 전달.
  dimensionFilters: AdminLogsFilter[]
  // 추가 query string (event_name 같은 비-필터 조건).
  extraQuery?: string
  // 패널 헤더에 보여줄 짧은 설명.
  description?: string
}

// I1 KPI 카드 데이터.
export type I1KpiData = {
  activeSessions: number
  funnelStarted: number
  funnelGoalReached: number
  funnelAbandoned: number
}

// I11 KPI 카드 데이터 (현 API는 session 근사치).
export type I11KpiData = {
  visitors: number
  completedVisitors: number
  // 완주율 % (0~100).
  completionRate: number
}

// I12 KPI 카드 데이터 — 결과 화면 체류 시간 4분위.
export type I12KpiData = {
  bounceUnder5s: number
  short5to30s: number
  normal30to60s: number
  immersedOver60s: number
}

// 차트별 비동기 상태 — KPI 훅이 공통으로 반환.
export type AnalyticsKpiState<T> = {
  data: T | null
  isLoading: boolean
  errorMessage: string | null
}
