// 결과 화면 액션 + 세그먼트 태그 메타데이터.
// 결과 단계(reveal) 메타와 mock fallback은 자동 카메라 메타포 시퀀스 도입과 함께 제거됨.
// useRelayResult 훅이 서버 응답(parts[].drawerNickname)으로 segments를 직접 생성한다.

import { Download, Share2 } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

import type { RelayRoundKey } from './rounds'

export interface RelayResultAction {
  label: string
  Icon: LucideIcon
}

export const RELAY_RESULT_ACTIONS: RelayResultAction[] = [
  { label: '보관함에', Icon: Download },
  { label: '광장에 전시하기', Icon: Share2 },
]

// ── 결과 세그먼트 (얼굴/몸통/다리 카드·태그 메타) ─────────────────────
export interface RelayResultSegment {
  key: RelayRoundKey
  participantName: string
  roleLabel: string
  tagLabel: string
  tagClassName: string
}

// 세그먼트 태그 배경색 매핑.
export const SEGMENT_TAG_CLASSNAMES: Record<RelayRoundKey, string> = {
  face: 'bg-relay-active',
  body: 'bg-relay-segment-body',
  legs: 'bg-relay-segment-legs',
}
