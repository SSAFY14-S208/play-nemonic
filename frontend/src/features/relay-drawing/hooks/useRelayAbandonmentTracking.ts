'use client'

import { useEffect, useRef } from 'react'

import { logEvent } from '@/shared/libs'

import { useRelayDrawingStore } from '../stores'

// 릴레이 룸 페이지 라이프사이클 동안의 이탈을 감지해 단일 이벤트로 발사한다.
// RelayRoomPageInner에 1회 마운트.
//
// 발사 규칙 — cleanup 시 마지막으로 관측된 페이즈에 따라 1종만:
//   - lobby (roomStatus === 'WAITING')              → room_lobby_abandoned
//   - drawing (PLAYING/FINALIZING) + isSubmitted=false → creation_abandoned
//   - result (FINISHED) + 사용자 share/save 액션 없음  → result_share_abandoned
//   - drawing + isSubmitted=true                    → 발사 안 함 (제출 후 자연 종료로 간주)
//   - dismissalReason이 set된 자연 종료              → 발사 안 함
//
// 단순 새로고침은 LogBootstrap의 pagehide/visibilitychange가 비콘으로 처리한다.
// 이 훅은 SPA 라우터 push로 이탈한 경우(unmount)를 잡는다.
export function useRelayAbandonmentTracking(): void {
  const roomStatus = useRelayDrawingStore((state) => state.roomStatus)
  const roomCode = useRelayDrawingStore((state) => state.roomCode)
  const participants = useRelayDrawingStore((state) => state.participants)
  const dismissalReason = useRelayDrawingStore((state) => state.dismissalReason)
  const isSubmitted = useRelayDrawingStore((state) => state.isSubmitted)

  // 최신 관측 상태를 ref에 보관해 cleanup에서 사용한다 (cleanup은 마지막 렌더 시점의
  // closure를 보지만, ref가 더 명시적이고 다중 effect 사이의 일관성을 확보하기 좋다).
  const latestRef = useRef({
    roomStatus,
    roomCode,
    participantCount: participants.length,
    dismissalReason,
    isSubmitted,
  })
  latestRef.current = {
    roomStatus,
    roomCode,
    participantCount: participants.length,
    dismissalReason,
    isSubmitted,
  }

  // 각 페이즈 진입 시각 — wait_time_ms, elapsed_ms, time_on_result_ms 계산용.
  const lobbyEnteredAtRef = useRef<number | null>(null)
  const drawingEnteredAtRef = useRef<number | null>(null)
  const resultEnteredAtRef = useRef<number | null>(null)

  useEffect(() => {
    if (roomStatus === 'WAITING' && lobbyEnteredAtRef.current === null) {
      lobbyEnteredAtRef.current = Date.now()
    }
    if (
      (roomStatus === 'PLAYING' || roomStatus === 'FINALIZING') &&
      drawingEnteredAtRef.current === null
    ) {
      drawingEnteredAtRef.current = Date.now()
    }
    if (roomStatus === 'FINISHED' && resultEnteredAtRef.current === null) {
      resultEnteredAtRef.current = Date.now()
    }
  }, [roomStatus])

  useEffect(() => {
    // cleanup 한 번만 등록. 의존성이 비어 있어야 mount/unmount 1회 보장.
    return () => {
      const {
        roomStatus: finalStatus,
        roomCode: finalRoomCode,
        participantCount,
        dismissalReason: finalDismissal,
        isSubmitted: finalIsSubmitted,
      } = latestRef.current

      // 호스트 종료 / 강퇴 / 방 닫힘 등 명시적 종료는 abandonment 아님.
      if (finalDismissal) return

      if (finalStatus === 'WAITING' && lobbyEnteredAtRef.current !== null) {
        logEvent('room_lobby_abandoned', {
          contentType: 'relay',
          roomId: finalRoomCode ?? undefined,
          metadata: {
            content_type: 'relay',
            room_id: finalRoomCode ?? undefined,
            wait_time_ms: Date.now() - lobbyEnteredAtRef.current,
            participant_count: participantCount,
          },
        })
        return
      }

      if (
        (finalStatus === 'PLAYING' || finalStatus === 'FINALIZING') &&
        !finalIsSubmitted &&
        drawingEnteredAtRef.current !== null
      ) {
        logEvent('creation_abandoned', {
          contentType: 'relay',
          roomId: finalRoomCode ?? undefined,
          metadata: {
            content_type: 'relay',
            funnel_name: 'relay_room_creation',
            step_name: 'drawing',
            elapsed_ms: Date.now() - drawingEnteredAtRef.current,
          },
        })
        return
      }

      if (finalStatus === 'FINISHED' && resultEnteredAtRef.current !== null) {
        logEvent('result_share_abandoned', {
          contentType: 'relay',
          roomId: finalRoomCode ?? undefined,
          metadata: {
            content_type: 'relay',
            time_on_result_ms: Date.now() - resultEnteredAtRef.current,
          },
        })
      }
    }
  }, [])
}
