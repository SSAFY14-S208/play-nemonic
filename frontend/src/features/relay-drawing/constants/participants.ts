// 부스 미리보기 카드용 mock 참가자 데이터.
// 실제 참여자 목록은 store.participants(서버 hydrate)에서 읽는다 — 이 상수는
// 부스 화면의 정적 일러스트 용도다.

import { Cat, Crown, Squirrel, type LucideIcon } from 'lucide-react'

export interface RelayParticipant {
  id: string
  name: string
  avatar: string
  role: string
  tone: 'fox' | 'cat' | 'bear'
  Icon: LucideIcon
}

export const RELAY_PARTICIPANTS: RelayParticipant[] = [
  { id: 'fox', name: '여우 (나)', avatar: '여', role: '방장', tone: 'fox', Icon: Squirrel },
  { id: 'cat', name: '고양이', avatar: '고', role: '얼굴', tone: 'cat', Icon: Cat },
  { id: 'bear', name: '곰돌이', avatar: '곰', role: '다리', tone: 'bear', Icon: Crown },
]
