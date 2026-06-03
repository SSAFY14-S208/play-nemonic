'use client'

import type { RelaySocketStatus } from '@/shared/libs'

import { useRelayRoomHydration } from './useRelayRoomHydration'
import { useRelayRoomSocket } from './useRelayRoomSocket'

interface UseRelayRoomReturn {
  isHydrating: boolean
  hydrationError: string | null
  socketStatus: RelaySocketStatus
}

/**
 * RelayRoomPage가 URL의 roomCode로 호출하는 마스터 훅.
 *
 * REST hydration(useRelayRoomHydration)과 WS 이벤트 처리(useRelayRoomSocket)를
 * 조합해 방 상태를 store에 항상 최신으로 유지한다.
 *
 * 책임:
 *   1. 마운트 시 REST(getRelayRoom)로 방 상태 hydrate — 새로고침/직접 URL 진입
 *      에서도 store가 항상 서버 기준 최신 스냅샷으로 시작하게 한다 (가이드 §25).
 *   2. WebSocket 연결 + 이벤트 → store 라우팅 — 이후의 변화는 WS로 받음.
 *   3. KICKED_FROM_ROOM / DUPLICATE_SESSION_CLOSED 같은 종료성 개인 큐 이벤트
 *      수신 시 룸 정리 + 부스 이동.
 *
 * 책임 아님:
 *   - REST/WS 결과로 view를 어떻게 렌더할지 — RelayRoomPage 자체가 roomStatus
 *     로 분기. 본 훅은 store 갱신만 담당.
 */
export function useRelayRoom(roomCode: string | null): UseRelayRoomReturn {
  const { hydrationError, isHydrating, isViewerParticipant } =
    useRelayRoomHydration(roomCode)

  const { socketStatus } = useRelayRoomSocket({
    roomCode,
    enabled: !hydrationError && isViewerParticipant,
  })

  return { isHydrating, hydrationError, socketStatus }
}
