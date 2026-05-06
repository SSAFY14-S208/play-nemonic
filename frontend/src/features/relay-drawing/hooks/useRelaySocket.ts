'use client'

import { useEffect, useRef, useState } from 'react'

import {
  connectRelaySocketForCurrentUser,
  disconnectRelaySocket,
  sendRelayPing,
  type RelaySocketStatus,
} from '@/shared/libs'
import { useUserStore } from '@/shared/stores'
import type { RelayWsEvent, RelayWsEventType } from '@/shared/types'

// 가이드 §22 — 클라이언트 ping 주기. STOMP heartbeat(10s)와 중복되지 않게 살짝
// 길게 둔다. 서버가 룸 단위로 "이 사용자 살아있음"을 카운트하는 용도라서
// 1분 안에 한두 번이면 충분하다.
const PING_INTERVAL_MS = 25_000

export type RelayEventHandler<TType extends RelayWsEventType> = (
  event: Extract<RelayWsEvent, { type: TType }>,
) => void

// 이벤트별 핸들러 객체. 모든 이벤트가 선택적이다 — 화면(로비/게임/결과)마다
// 관심 있는 이벤트가 다르기 때문이다.
//
// 예) 로비 화면: PARTICIPANT_*, SETTINGS_CHANGED, GAME_STARTED, HOST_CHANGED만 처리
//      게임 화면: PART_*, ALL_PARTS_COMPLETED만 처리
export type RelayEventHandlers = {
  [TType in RelayWsEventType]?: RelayEventHandler<TType>
}

interface UseRelaySocketArgs {
  // null/undefined면 연결을 시도하지 않는다 — 라우트 진입 직후 roomCode가 아직
  // 결정되지 않았을 때를 위해 명시적으로 nullable로 받는다.
  roomCode: string | null | undefined
  handlers: RelayEventHandlers
  // 기본 true. 결과 화면처럼 "이미 끝난 방에서 재구독만 하고 싶다"가 아니면
  // 그대로 둔다.
  enabled?: boolean
}

interface UseRelaySocketReturn {
  status: RelaySocketStatus
  // STOMP 연결이 살아있는지 — UI에서 "재연결 중" 배너 등에 사용.
  isConnected: boolean
  // 자동 재연결 사이클 중인지 — 끊김 오버레이 표시 트리거.
  isReconnecting: boolean
}

/**
 * 룸 WebSocket 연결을 라이프사이클에 묶는 훅.
 *
 * 책임:
 *   1. roomCode가 들어오면 STOMP 연결을 활성화한다.
 *   2. 토픽/큐로 들어오는 이벤트를 type별로 handlers에 라우팅한다.
 *   3. 페이지 이탈 시 연결을 정리한다 (강퇴/중복세션은 호출 측이 직접 disconnectRelaySocket).
 *   4. 주기적으로 ping을 쏴 룸 단위 liveness를 유지한다.
 *
 * 책임 아닌 것:
 *   - 이벤트 → store write. 이건 호출 측 화면 훅(useRelayLobby 등)에서 한다.
 *     이 훅은 라우터 역할만 한다.
 *   - REST 호출 (PART_STARTED 후 getAssignmentMe 등). 마찬가지로 호출 측에서.
 */
export const useRelaySocket = ({
  roomCode,
  handlers,
  enabled = true,
}: UseRelaySocketArgs): UseRelaySocketReturn => {
  const userUuid = useUserStore((state) => state.userUuid)
  const [status, setStatus] = useState<RelaySocketStatus>('idle')

  // handlers를 ref로 들고 다닌다 — 이벤트 콜백은 connect 시점에 한 번 등록되므로
  // 호출 측이 매 렌더마다 새 객체를 넘겨도 의존성 재구독이 일어나지 않게 한다.
  // 같은 이유로 handlers는 아래 connect effect deps에서 빠진다.
  //
  // 갱신은 effect 안에서 한다 — 렌더 중 ref.current에 쓰면 React Compiler/StrictMode
  // 규칙(렌더 순수성)을 깬다. STOMP 콜백은 비동기로 commit 이후에 실행되므로
  // effect로 갱신해도 디스패치 전에 항상 최신값이 반영된다.
  const handlersRef = useRef<RelayEventHandlers>(handlers)
  useEffect(() => {
    handlersRef.current = handlers
  })

  useEffect(() => {
    if (!enabled || !roomCode || !userUuid) return

    const dispatch = (event: RelayWsEvent) => {
      // discriminated union narrowing — TS가 event.type별로 data를 좁혀준다.
      // 핸들러의 정확한 시그니처를 유지하려고 좀 길게 풀어놓는다.
      switch (event.type) {
        case 'PARTICIPANT_CONNECTED':
          handlersRef.current.PARTICIPANT_CONNECTED?.(event)
          break
        case 'PARTICIPANT_DROPPED':
          handlersRef.current.PARTICIPANT_DROPPED?.(event)
          break
        case 'SETTINGS_CHANGED':
          handlersRef.current.SETTINGS_CHANGED?.(event)
          break
        case 'GAME_STARTED':
          handlersRef.current.GAME_STARTED?.(event)
          break
        case 'PART_STARTED':
          handlersRef.current.PART_STARTED?.(event)
          break
        case 'PART_SUBMITTED':
          handlersRef.current.PART_SUBMITTED?.(event)
          break
        case 'PART_AUTO_SUBMITTED':
          handlersRef.current.PART_AUTO_SUBMITTED?.(event)
          break
        case 'ALL_PARTS_COMPLETED':
          handlersRef.current.ALL_PARTS_COMPLETED?.(event)
          break
        case 'RESULT_CREATED':
          handlersRef.current.RESULT_CREATED?.(event)
          break
        case 'HOST_CHANGED':
          handlersRef.current.HOST_CHANGED?.(event)
          break
        case 'ROOM_CLOSED':
          handlersRef.current.ROOM_CLOSED?.(event)
          break
        case 'KICKED_FROM_ROOM':
          handlersRef.current.KICKED_FROM_ROOM?.(event)
          break
        case 'DUPLICATE_SESSION_CLOSED':
          handlersRef.current.DUPLICATE_SESSION_CLOSED?.(event)
          break
      }
    }

    const ok = connectRelaySocketForCurrentUser({
      roomCode,
      onEvent: dispatch,
      onStatusChange: setStatus,
    })
    if (!ok) return

    const pingTimer = window.setInterval(sendRelayPing, PING_INTERVAL_MS)

    return () => {
      window.clearInterval(pingTimer)
      disconnectRelaySocket()
    }
  }, [enabled, roomCode, userUuid])

  return {
    status,
    isConnected: status === 'connected',
    isReconnecting: status === 'reconnecting',
  }
}
