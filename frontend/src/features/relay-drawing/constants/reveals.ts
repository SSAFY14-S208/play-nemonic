// 결과 화면 액션 + 세그먼트 태그 메타데이터.
// 결과 단계(reveal) 메타와 mock fallback은 자동 카메라 메타포 시퀀스 도입과 함께 제거됨.
// useRelayResult 훅이 서버 응답(parts[].drawerNickname)으로 segments를 직접 생성한다.

import { Download, Send, Share2 } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

import type { RelayRoundKey } from './rounds'

export interface RelayResultAction {
  id: 'download' | 'community-post' | 'external-share'
  label: string
  Icon: LucideIcon
}

export const RELAY_RESULT_ACTIONS: RelayResultAction[] = [
  { id: 'download', label: '다운로드', Icon: Download },
  { id: 'community-post', label: '커뮤니티 게시', Icon: Share2 },
  { id: 'external-share', label: '외부 공유', Icon: Send },
]

// 결과 화면에서 작품 자동 전환 간격 (ms).
// 리빌 시퀀스(메모지 stagger → 정렬 → overlay → final)가 약 3~3.5초 내에 끝나므로,
// 7초면 final phase에서 잠시 머무른 뒤 다음 작품으로 자연스럽게 넘어간다.
export const RELAY_RESULT_AUTO_ADVANCE_MS = 7000

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
