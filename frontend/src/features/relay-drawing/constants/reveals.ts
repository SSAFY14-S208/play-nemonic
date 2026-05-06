// 결과 화면 순차 공개 단계 메타데이터 + 결과 액션.
// 현재의 `participantName`/`avatar`는 mock이며, 실제 결과 wiring 시
// `getRelayRoomResults` 응답의 `parts[].drawerNickname`으로 교체된다.

import { Download, Share2 } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

export type RelayResultRevealStep = 'face' | 'body' | 'legs' | 'final'

export interface RelayResultReveal {
  key: RelayResultRevealStep
  order: number
  roleLabel: string
  participantName: string
  participantDisplayName: string
  avatar: string
  titleSuffix: string
  spotlightLabel: string
  nextLabel: string
}

export const RELAY_RESULT_REVEALS: RelayResultReveal[] = [
  {
    key: 'face',
    order: 1,
    roleLabel: '얼굴',
    participantName: '고양이',
    participantDisplayName: '고양이',
    avatar: '🐱',
    titleSuffix: '가 시작했어요',
    spotlightLabel: '방금 그린 사람',
    nextLabel: '다음 ▶',
  },
  {
    key: 'body',
    order: 2,
    roleLabel: '몸통',
    participantName: '여우',
    participantDisplayName: '여우 (나)',
    avatar: '🦊',
    titleSuffix: '가 이어 그렸어요',
    spotlightLabel: '방금 그린 사람',
    nextLabel: '다음 ▶',
  },
  {
    key: 'legs',
    order: 3,
    roleLabel: '다리',
    participantName: '곰돌이',
    participantDisplayName: '곰돌이',
    avatar: '🐻',
    titleSuffix: '가 마무리했어요',
    spotlightLabel: '방금 그린 사람',
    nextLabel: '결과 보기 ▶',
  },
  {
    key: 'final',
    order: 4,
    roleLabel: '완성',
    participantName: '고양이',
    participantDisplayName: '고양이',
    avatar: '🐱',
    titleSuffix: '님의 캐릭터',
    spotlightLabel: '합쳐진 캐릭터',
    nextLabel: '완성',
  },
]

export interface RelayResultAction {
  label: string
  Icon: LucideIcon
}

export const RELAY_RESULT_ACTIONS: RelayResultAction[] = [
  { label: '보관함에', Icon: Download },
  { label: '광장에 전시하기', Icon: Share2 },
]
