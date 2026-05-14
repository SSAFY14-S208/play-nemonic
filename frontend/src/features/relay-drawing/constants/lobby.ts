// 로비 / 방 진입 화면에서 사용하는 상수.
// `RELAY_ROOM_CODE`는 store.roomCode가 아직 hydrate 되지 않은 시점의 폴백용
// 더미값. wiring 단계에서 폴백 자체를 제거하고 빈 상태 처리를 별도로 둔다.

import type { LucideIcon } from 'lucide-react'
import { Copy, Link2, QrCode } from 'lucide-react'
import type { RelayShareActionKey } from '../hooks'

// hydrate 전 store 초기값으로 사용되는 폴백 — 백엔드 응답(timeLimitAllowedSeconds)이
// 도착하면 덮어씌워진다.
export const DEFAULT_TIME_LIMIT_ALLOWED_SECONDS = [30, 45, 60]
export const DEFAULT_TIME_LIMIT_SECONDS = DEFAULT_TIME_LIMIT_ALLOWED_SECONDS[0]

export interface RelayLobbyShareAction {
  key: RelayShareActionKey
  label: string
  Icon: LucideIcon
}

export const RELAY_SHARE_ACTIONS: RelayLobbyShareAction[] = [
  { key: 'copyLink', label: '링크 복사', Icon: Link2 },
  { key: 'copyRoomCode', label: '코드 복사', Icon: Copy },
]

export const RELAY_QR_ACTION: RelayLobbyShareAction = {
  key: 'qrCode',
  label: 'QR 코드',
  Icon: QrCode,
}

export const RELAY_ROOM_CODE = 'ABC123'
