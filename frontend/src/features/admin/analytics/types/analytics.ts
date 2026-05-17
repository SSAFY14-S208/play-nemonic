// "통계 및 분석" 페이지 (system observability) 내부 타입.

export type MetricsTimeRangePresetKey =
  | 'last-5m'
  | 'last-15m'
  | 'last-1h'
  | 'last-6h'
  | 'last-24h'
  | 'custom'

export type MetricsFiltersState = {
  preset: MetricsTimeRangePresetKey
  customFrom: string
  customTo: string
  autoRefresh: boolean
  // 새로고침 트리거용 단조 증가 카운터.
  refreshNonce: number
}

// 차트 비동기 상태 — 패널 훅이 공통으로 반환.
export type MetricsAsyncState<T> = {
  data: T | null
  isLoading: boolean
  errorMessage: string | null
}
