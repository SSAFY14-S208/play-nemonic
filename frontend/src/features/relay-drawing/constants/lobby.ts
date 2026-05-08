// 로비 / 방 진입 화면에서 사용하는 상수.
// `RELAY_ROOM_CODE`는 store.roomCode가 아직 hydrate 되지 않은 시점의 폴백용
// 더미값. wiring 단계에서 폴백 자체를 제거하고 빈 상태 처리를 별도로 둔다.

import type { LucideIcon } from 'lucide-react'
import { BadgeCheck, Link2 } from 'lucide-react'

export const RELAY_TIME_LIMITS_SECONDS = [30, 45, 60]
export const DEFAULT_TIME_LIMIT_SECONDS = 45

export interface RelayLobbyAction {
  label: string
  Icon: LucideIcon
}

export const RELAY_ACTIONS: RelayLobbyAction[] = [
  { label: '링크 복사', Icon: Link2 },
  { label: 'QR 코드', Icon: BadgeCheck },
]

export const RELAY_EMPTY_SLOTS = [
  '초대를 기다리는 중...',
  '초대를 기다리는 중...',
  '초대를 기다리는 중...',
]

export const RELAY_ROOM_CODE = 'ABC123'
