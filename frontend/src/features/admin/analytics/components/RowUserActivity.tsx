'use client'

import { ENTRY_COLORS, FUNNEL_COLORS, METRICS_COLORS } from '..'
import { useActiveByContentTypeTimeline, useActiveSessionCount, useActiveUuidCount, useEntryChannelTimeline, useFlipbookCompletionTimeline, useFunnelEventsTimeline, useWebSocketRate, useWebSocketSessions, type MetricsVizArgs } from '../hooks'

import { ChartFrame } from './ChartFrame'
import { MetricsLineChart } from './MetricsLineChart'
import { MetricsStatCard } from './MetricsStatCard'
import { VizCard } from './VizCard'

type Props = {
  args: MetricsVizArgs
  onRetry: () => void
}

const FUNNEL_LABEL: Record<string, string> = {
  relay_room_creation: '릴레이드로잉',
  flipbook_room_creation: '플립북',
  community_memo_posting: '커뮤니티 메모',
  fortune_creation: '오늘의 운세',
  gallery_save_share: '갤러리·공유',
}

const ENTRY_LABEL: Record<string, string> = {
  direct: '직접 접속',
  search: '검색',
  social: 'SNS',
  qr: 'QR 코드',
  share: '공유 링크',
  campaign: '캠페인',
  unknown: '알 수 없음',
}

const EVENT_LABEL: Record<string, string> = {
  funnel_started: '시작',
  funnel_goal_reached: '완료',
  funnel_abandoned: '이탈',
  funnel_step_completed: '단계 완료',
}

const EVENT_COLOR: Record<string, string> = {
  funnel_started: METRICS_COLORS.accent,
  funnel_goal_reached: METRICS_COLORS.great,
  funnel_abandoned: METRICS_COLORS.danger,
  funnel_step_completed: METRICS_COLORS.secondary,
}

const formatCount = (value: number) =>
  value.toLocaleString('ko-KR', { maximumFractionDigits: 0 })
const formatRps = (value: number) =>
  `${value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })} /s`

// ============================================================
function ActiveUuidPanel({ args, onRetry }: Props) {
  const state = useActiveUuidCount(args)
  return (
    <VizCard title="활성 사용자" subtitle="distinct uuid (선택 기간)" span={3} minHeight={160}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && state.data === null}
        onRetry={onRetry}
      >
        <MetricsStatCard
          value={formatCount(state.data ?? 0)}
          label="uuid"
          accentColor={METRICS_COLORS.accent}
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function ActiveSessionPanel({ args, onRetry }: Props) {
  const state = useActiveSessionCount(args)
  return (
    <VizCard title="활성 세션" subtitle="distinct session_id (선택 기간)" span={3} minHeight={160}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && state.data === null}
        onRetry={onRetry}
      >
        <MetricsStatCard
          value={formatCount(state.data ?? 0)}
          label="session_id"
          accentColor={METRICS_COLORS.secondary}
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function WebSocketSessionsPanel({ args, onRetry }: Props) {
  const state = useWebSocketSessions(args)
  const value = state.data?.series[0]?.value ?? null
  return (
    <VizCard title="WebSocket 활성 세션" subtitle="STOMP gauge" span={3} minHeight={160}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && value === null}
        onRetry={onRetry}
      >
        <MetricsStatCard
          value={value !== null ? formatCount(value) : '—'}
          label="active sessions"
          accentColor={METRICS_COLORS.great}
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function WebSocketRatePanel({ args, onRetry }: Props) {
  const state = useWebSocketRate(args)
  const rows = state.data?.rows ?? []
  const keys = state.data?.keys ?? []
  return (
    <VizCard title="WebSocket connect / disconnect" subtitle="rate" span={3} minHeight={160}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          labelFor={(key) => (key === 'connect' ? 'connect' : 'disconnect')}
          colorFor={(key) => (key === 'connect' ? METRICS_COLORS.great : METRICS_COLORS.danger)}
          valueFormatter={formatRps}
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function FunnelEventsPanel({ args, onRetry }: Props) {
  const state = useFunnelEventsTimeline(args)
  const rows = state.data?.rows ?? []
  const keys = state.data?.keys ?? []
  return (
    <VizCard
      title="Funnel 흐름"
      subtitle="started / goal / abandoned (분당)"
      span={6}
      minHeight={240}
    >
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          labelFor={(key) => EVENT_LABEL[key] ?? key}
          colorFor={(key) => EVENT_COLOR[key] ?? METRICS_COLORS.muted}
          valueFormatter={formatCount}
          connectNulls
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function ActiveByContentTypePanel({ args, onRetry }: Props) {
  const state = useActiveByContentTypeTimeline(args)
  const rows = state.data?.rows ?? []
  const keys = state.data?.keys ?? []
  return (
    <VizCard title="콘텐츠별 진입 추이" subtitle="funnel_started" span={6} minHeight={240}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          labelFor={(key) => FUNNEL_LABEL[key] ?? key}
          colorFor={(key) =>
            FUNNEL_COLORS[key] ?? METRICS_COLORS.muted
          }
          valueFormatter={formatCount}
          connectNulls
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function EntryChannelTimelinePanel({ args, onRetry }: Props) {
  const state = useEntryChannelTimeline(args)
  const rows = state.data?.rows ?? []
  const keys = state.data?.keys ?? []
  return (
    <VizCard title="채널 유입 추이" subtitle="entry_type" span={6} minHeight={240}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          labelFor={(key) => ENTRY_LABEL[key] ?? key}
          colorFor={(key) =>
            ENTRY_COLORS[key] ?? METRICS_COLORS.muted
          }
          valueFormatter={formatCount}
          connectNulls
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
function FlipbookCompletionPanel({ args, onRetry }: Props) {
  const state = useFlipbookCompletionTimeline(args)
  const rows = state.data?.rows ?? []
  const keys = state.data?.keys ?? []
  return (
    <VizCard title="플립북 완성 도달률" subtitle="event 분당" span={6} minHeight={240}>
      <ChartFrame
        isLoading={state.isLoading}
        errorMessage={state.errorMessage}
        isEmpty={!state.errorMessage && rows.length === 0}
        onRetry={onRetry}
      >
        <MetricsLineChart
          rows={rows}
          seriesKeys={keys}
          labelFor={(key) => EVENT_LABEL[key] ?? key}
          colorFor={(key) => EVENT_COLOR[key] ?? METRICS_COLORS.muted}
          valueFormatter={formatCount}
          connectNulls
        />
      </ChartFrame>
    </VizCard>
  )
}

// ============================================================
export function RowUserActivity({ args, onRetry }: Props) {
  return (
    <>
      <ActiveUuidPanel args={args} onRetry={onRetry} />
      <ActiveSessionPanel args={args} onRetry={onRetry} />
      <WebSocketSessionsPanel args={args} onRetry={onRetry} />
      <WebSocketRatePanel args={args} onRetry={onRetry} />
      <ActiveByContentTypePanel args={args} onRetry={onRetry} />
      <EntryChannelTimelinePanel args={args} onRetry={onRetry} />
      <FunnelEventsPanel args={args} onRetry={onRetry} />
      <FlipbookCompletionPanel args={args} onRetry={onRetry} />
    </>
  )
}
