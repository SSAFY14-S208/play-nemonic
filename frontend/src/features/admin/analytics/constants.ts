import type { MetricsTimeRangePresetKey } from './types'

// 자동 갱신 주기 — system observability는 빠르게 변동하므로 15초.
export const METRICS_AUTO_REFRESH_INTERVAL_MS = 15_000

// 시간 범위 프리셋. marketing dashboard와 다름 — 더 짧은 범위 중심.
export const METRICS_TIME_RANGE_PRESETS: ReadonlyArray<{
  key: MetricsTimeRangePresetKey
  label: string
  durationMs: number | null
}> = [
  { key: 'last-5m', label: '최근 5분', durationMs: 5 * 60 * 1000 },
  { key: 'last-15m', label: '최근 15분', durationMs: 15 * 60 * 1000 },
  { key: 'last-1h', label: '최근 1시간', durationMs: 60 * 60 * 1000 },
  { key: 'last-6h', label: '최근 6시간', durationMs: 6 * 60 * 60 * 1000 },
  { key: 'last-24h', label: '최근 24시간', durationMs: 24 * 60 * 60 * 1000 },
  { key: 'custom', label: '커스텀', durationMs: null },
] as const

// Prometheus rate window. 시간 범위 프리셋에 맞춰 자동 선택.
export const rateWindowFor = (
  preset: MetricsTimeRangePresetKey,
): '1m' | '5m' | '15m' => {
  if (preset === 'last-5m') return '1m'
  if (preset === 'last-15m') return '1m'
  if (preset === 'last-1h') return '5m'
  return '15m'
}

// query_range step 자동 선택 (BE 검증: 5s~1h).
export const stepFor = (preset: MetricsTimeRangePresetKey): string => {
  if (preset === 'last-5m') return '15s'
  if (preset === 'last-15m') return '30s'
  if (preset === 'last-1h') return '1m'
  if (preset === 'last-6h') return '5m'
  if (preset === 'last-24h') return '10m'
  return '1m'
}

// `up{job}` 화이트리스트 — BE의 MetricsTemplateRegistry.JOB_NAMES와 정확히 일치해야 함.
// 실제 job 이름은 monitoring/prometheus/prometheus.yml 의 scrape_configs `job_name` 그대로.
export const SERVICE_UP_TARGETS = [
  { job: 'prometheus', label: 'Prometheus' },
  { job: 'node', label: 'Node Exporter' },
  { job: 'cadvisor', label: 'cAdvisor' },
  { job: 'spring-app', label: 'Spring App' },
  { job: 'moderation-server', label: 'Moderation' },
  { job: 'nginx', label: 'Nginx' },
] as const

// funnel content 5종 색 — marketing dashboard와 동일 매핑 (사용자 인식 일관성).
export const FUNNEL_COLORS: Record<string, string> = {
  relay_room_creation: 'hsl(258 90% 76%)',
  flipbook_room_creation: 'hsl(330 81% 70%)',
  community_memo_posting: 'hsl(199 95% 60%)',
  fortune_creation: 'hsl(43 96% 56%)',
  gallery_save_share: 'hsl(160 64% 52%)',
}

// 유입 채널 7종 색 — marketing dashboard와 동일.
export const ENTRY_COLORS: Record<string, string> = {
  direct: 'hsl(215 16% 47%)',
  search: 'hsl(199 95% 60%)',
  social: 'hsl(258 90% 76%)',
  qr: 'hsl(43 96% 56%)',
  share: 'hsl(330 81% 70%)',
  campaign: 'hsl(160 64% 52%)',
  unknown: 'hsl(215 19% 35%)',
}

// 차트 전용 팔레트 — HSL 문자열. dashboard와 다른 의미 색 set.
export const METRICS_COLORS = {
  great: 'hsl(160 84% 39%)', // up
  danger: 'hsl(351 89% 60%)', // down / error
  warn: 'hsl(20 95% 65%)', // partial / latency p99
  neutral: 'hsl(43 96% 56%)', // latency p95
  accent: 'hsl(258 90% 76%)', // primary line (rps, latency p50)
  secondary: 'hsl(199 95% 60%)', // secondary (in / connect)
  tertiary: 'hsl(330 81% 70%)', // tertiary (out / disconnect)
  muted: 'hsl(215 16% 47%)',
} as const

// p50/p95/p99 색 매핑.
export const LATENCY_QUANTILE_COLORS: Record<string, string> = {
  '0.5': METRICS_COLORS.accent,
  '0.95': METRICS_COLORS.neutral,
  '0.99': METRICS_COLORS.warn,
}

// 5개 row 메타 — 페이지 골격 렌더링 용도.
export const ROW_META = [
  { key: 'service-status', title: '서비스 상태' },
  { key: 'request-response', title: '요청 · 응답' },
  { key: 'user-activity', title: '사용자 행동' },
  { key: 'content-activity', title: '콘텐츠 활성' },
  { key: 'host-resources', title: '서버 리소스' },
] as const
