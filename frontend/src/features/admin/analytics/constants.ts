import type { LogsTimeRangePresetKey, ViizMeta } from './types'

// 자동 갱신 주기 — 30초 고정. UI 토글로 ON/OFF만 제공.
export const AUTO_REFRESH_INTERVAL_MS = 30_000

// 시간 범위 프리셋. 'custom'은 별도 UI로 from/to ISO 직접 입력.
export const TIME_RANGE_PRESETS: ReadonlyArray<{
  key: LogsTimeRangePresetKey
  label: string
  durationMs: number | null
}> = [
  { key: 'last-1h', label: '최근 1시간', durationMs: 60 * 60 * 1000 },
  { key: 'last-24h', label: '최근 24시간', durationMs: 24 * 60 * 60 * 1000 },
  { key: 'last-7d', label: '최근 7일', durationMs: 7 * 24 * 60 * 60 * 1000 },
  { key: 'last-30d', label: '최근 30일', durationMs: 30 * 24 * 60 * 60 * 1000 },
  { key: 'custom', label: '커스텀', durationMs: null },
] as const

// service 화이트리스트 — 백엔드 LogsQueryBuilder가 받는 service 값.
// 멀티 셀렉트로 OR 조건 필터.
export const SERVICE_OPTIONS = [
  { value: 'client-web', label: 'client-web' },
  { value: 'api-server', label: 'api-server' },
] as const

// OSD 마케팅 대시보드 funnel 색 매핑 — 디자인 시스템에 동등 토큰이 없어서 chart 전용
// 팔레트로 분리. HSL로 정의해 향후 dark mode 호환 + Tailwind v4 inline 사용 시 그대로 전달.
// 원본 hex: violet/pink/sky/amber/emerald (OSD `FUNNEL_COLORS` 참고).
export const CHART_FUNNEL_COLORS = {
  relay_room_creation: 'hsl(258 90% 76%)', // #A78BFA
  flipbook_room_creation: 'hsl(330 81% 70%)', // #F472B6
  community_memo_posting: 'hsl(199 95% 60%)', // #38BDF8
  fortune_creation: 'hsl(43 96% 56%)', // #FBBF24
  gallery_save_share: 'hsl(160 64% 52%)', // #34D399
} as const

export const CHART_ENTRY_COLORS = {
  direct: 'hsl(215 16% 47%)', // #94A3B8
  search: 'hsl(199 95% 60%)', // #38BDF8
  social: 'hsl(258 90% 76%)', // #A78BFA
  qr: 'hsl(43 96% 56%)', // #FBBF24
  share: 'hsl(330 81% 70%)', // #F472B6
  campaign: 'hsl(160 64% 52%)', // #34D399
  unknown: 'hsl(215 19% 35%)', // #475569
} as const

// 신호등 (status) 색 — I1·I12 KPI 카드의 의미 색.
export const CHART_STATUS_COLORS = {
  danger: 'hsl(351 89% 60%)', // #F43F5E
  warn: 'hsl(20 95% 65%)', // #FB923C
  neutral: 'hsl(43 96% 56%)', // #FBBF24
  good: 'hsl(160 64% 52%)', // #34D399
  great: 'hsl(160 84% 39%)', // #10B981
  muted: 'hsl(215 16% 47%)', // #64748B
  accent: 'hsl(258 90% 76%)', // #A78BFA
} as const

// 13개 viz 메타 — 페이지 골격에서 placeholder/실제 컴포넌트 결정 + 섹션 제목용.
export const VIZ_META: ViizMeta[] = [
  {
    id: 'I1',
    section: 'overview',
    title: '오늘의 핵심 지표',
    subtitle: '활성 세션 · 진입 · 완료 · 이탈',
    status: 'live',
  },
  {
    id: 'I11',
    section: 'overview',
    title: '방문자 완주율',
    subtitle: '결과 도달 대비 전체 (세션 근사치)',
    status: 'live',
  },
  {
    id: 'I7',
    section: 'channel',
    title: '유입 경로 비율',
    subtitle: 'direct · search · social · share · qr · campaign',
    status: 'pending',
  },
  {
    id: 'I10',
    section: 'channel',
    title: '시간대별 유입원 추이',
    subtitle: 'entry_type 1시간 단위 누적',
    status: 'pending',
  },
  {
    id: 'I8',
    section: 'channel',
    title: 'SNS 유입 비율',
    subtitle: 'referrer host — 인스타·트위터·카카오 등',
    status: 'pending',
  },
  {
    id: 'I6',
    section: 'channel',
    title: '결과 도달 후 공유 비율',
    subtitle: '컨텐츠별 결과 도달 대비 공유 (근사치)',
    status: 'pending',
  },
  {
    id: 'I2',
    section: 'content',
    title: '컨텐츠별 완주율',
    subtitle: '진입 → 완료 비율 (%)',
    status: 'pending',
  },
  {
    id: 'I5',
    section: 'content',
    title: '시간대별 진입 추이',
    subtitle: 'funnel_started 컨텐츠별 1h 누적',
    status: 'pending',
  },
  {
    id: 'I3',
    section: 'flow',
    title: '단계별 이탈 깔때기',
    subtitle: '컨텐츠별 step 진행 잔존',
    status: 'pending',
  },
  {
    id: 'I4',
    section: 'flow',
    title: '컨텐츠 간 이동 흐름',
    subtitle: '카테고리 간 이동 빈도 (heatmap)',
    status: 'pending',
  },
  {
    id: 'I9',
    section: 'retention',
    title: '체험 공간 평균 체류 시간',
    subtitle: 'page_leave time_on_page_ms 평균',
    status: 'pending',
  },
  {
    id: 'I12',
    section: 'retention',
    title: '결과 화면 체류 시간 분포',
    subtitle: '< 5초 · 5-30초 · 30-60초 · 60초+',
    status: 'live',
  },
  {
    id: 'I13',
    section: 'retention',
    title: '이탈 직전 평균 체류 시간',
    subtitle: 'lobby · creation · result 단계별',
    status: 'pending',
  },
]

export const SECTION_META = [
  { key: 'overview', title: '오늘의 KPI' },
  { key: 'channel', title: '유입 채널' },
  { key: 'content', title: '컨텐츠' },
  { key: 'flow', title: '단계 · 화면 흐름' },
  { key: 'retention', title: '체류 · 이탈' },
] as const
