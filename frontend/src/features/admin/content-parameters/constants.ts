// 컨텐츠 파라미터 메타 — 백엔드 system-parameters 키 ↔ 화면 표시 정보 매핑.
//
// 백엔드의 두 네이밍 컨벤션을 모두 보관한다:
//   - `backendKey`: GET 응답의 `key` (dotted snake, 예: `community.max_memo_count`)
//   - `patchKey`:   PATCH 요청 body의 필드명 (camelCase, 예: `communityMaxMemoCount`)

import type { SystemParameterPatchKey } from '@/shared/types'

export type ParameterCategory = 'community' | 'relay' | 'flipbook' | 'fortune' | 'cs'

export type ParameterUnit = 'count' | 'people' | 'seconds' | 'frames' | 'hours'

export type ParameterMeta =
  | {
      id: string
      type: 'integer'
      category: ParameterCategory
      backendKey: string
      patchKey: SystemParameterPatchKey
      title: string
      description: string
      unit: ParameterUnit
    }
  | {
      id: string
      type: 'range'
      category: ParameterCategory
      backendKey: string
      patchKey: SystemParameterPatchKey
      title: string
      description: string
      unit: ParameterUnit
    }
  | {
      id: string
      type: 'enum'
      category: ParameterCategory
      backendKey: string
      patchKey: SystemParameterPatchKey
      allowed: number[]
      title: string
      description: string
      unit: ParameterUnit
    }

export const CONTENT_PARAMETERS: ParameterMeta[] = [
  {
    id: 'community-max-memo-count',
    type: 'integer',
    category: 'community',
    backendKey: 'community.max_memo_count',
    patchKey: 'communityMaxMemoCount',
    title: '커뮤니티 캔버스 최대 메모 수',
    description: '커뮤니티 캔버스 표시 메모 수 제한',
    unit: 'count',
  },
  {
    id: 'relay-room-participant-limit',
    type: 'range',
    category: 'relay',
    backendKey: 'relay.room_participant_limit',
    patchKey: 'relayRoomParticipantLimit',
    title: '릴레이 드로잉 최소 / 최대 인원',
    description: '릴레이 방 참여 인원 제한',
    unit: 'people',
  },
  {
    id: 'relay-room-time-limit-seconds',
    type: 'enum',
    category: 'relay',
    backendKey: 'relay.room_time_limit_seconds',
    patchKey: 'relayRoomTimeLimitSeconds',
    allowed: [30, 45, 60],
    title: '릴레이 드로잉 제한 시간',
    description: '릴레이 방 그리기 제한 시간',
    unit: 'seconds',
  },
  {
    id: 'relay-reconnect-grace-seconds',
    type: 'integer',
    category: 'relay',
    backendKey: 'relay.reconnect_grace_seconds',
    patchKey: 'relayReconnectGraceSeconds',
    title: '릴레이 재연결 유예 시간',
    description: '릴레이 진행 중 재연결 유예 시간',
    unit: 'seconds',
  },
  {
    id: 'flipbook-room-participant-limit',
    type: 'range',
    category: 'flipbook',
    backendKey: 'flipbook.room_participant_limit',
    patchKey: 'flipbookRoomParticipantLimit',
    title: '플립북 최소 / 최대 인원',
    description: '플립북 방 참여 인원 제한',
    unit: 'people',
  },
  {
    id: 'flipbook-room-time-limit-seconds',
    type: 'enum',
    category: 'flipbook',
    backendKey: 'flipbook.room_time_limit_seconds',
    patchKey: 'flipbookRoomTimeLimitSeconds',
    allowed: [30, 45, 60],
    title: '플립북 제한 시간',
    description: '플립북 방 그리기 제한 시간',
    unit: 'seconds',
  },
  {
    id: 'flipbook-min-frames',
    type: 'integer',
    category: 'flipbook',
    backendKey: 'flipbook.min_frames_per_flipbook',
    patchKey: 'flipbookMinFramesPerFlipbook',
    title: '완성 플립북 최소 프레임 수',
    description: '완성 플립북 최소 프레임 수',
    unit: 'frames',
  },
  {
    id: 'flipbook-reconnect-grace-seconds',
    type: 'integer',
    category: 'flipbook',
    backendKey: 'flipbook.reconnect_grace_seconds',
    patchKey: 'flipbookReconnectGraceSeconds',
    title: '플립북 재연결 유예 시간',
    description: '플립북 진행 중 재연결 유예 시간',
    unit: 'seconds',
  },
  {
    id: 'fortune-daily-limit',
    type: 'integer',
    category: 'fortune',
    backendKey: 'fortune.daily_limit',
    patchKey: 'fortuneDailyLimit',
    title: '오늘의 운세 일일 제한',
    description: '익명 사용자별 일일 운세 생성 제한',
    unit: 'count',
  },
  {
    id: 'cs-inquiry-unresolved-alert-hours',
    type: 'integer',
    category: 'cs',
    backendKey: 'cs_inquiry.unresolved_alert_threshold_hours',
    patchKey: 'csInquiryUnresolvedAlertThresholdHours',
    title: 'CS 문의 경보 기준',
    description: '미해결 고객 문의 알림 기준 시간',
    unit: 'hours',
  },
]

export const UNIT_LABEL: Record<ParameterUnit, string> = {
  count: '개',
  people: '명',
  seconds: '초',
  frames: '프레임',
  hours: '시간',
}

// 카테고리 chip — 페이지 전용 시각적 분류. semantic 토큰에 카테고리 색이 없어
// Tailwind 기본 팔레트를 한정적으로 사용.
export const CATEGORY_BADGE: Record<ParameterCategory, { label: string; chipClass: string }> = {
  community: {
    label: '커뮤니티',
    chipClass: 'bg-sky-100 text-sky-700',
  },
  relay: {
    label: '릴레이',
    chipClass: 'bg-amber-100 text-amber-700',
  },
  flipbook: {
    label: '플립북',
    chipClass: 'bg-rose-100 text-rose-700',
  },
  fortune: {
    label: '운세',
    chipClass: 'bg-violet-100 text-violet-700',
  },
  cs: {
    label: 'CS',
    chipClass: 'bg-slate-100 text-slate-700',
  },
}
