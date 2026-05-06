'use client'

import { HTTPError } from 'ky'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { ApiError, getRelayRoom } from '@/shared/apis'
import type { RelaySocketStatus } from '@/shared/libs'

import { useRelayDrawingStore } from '../stores'
import { useRelaySocket } from './useRelaySocket'

interface UseRelayRoomReturn {
  isHydrating: boolean
  hydrationError: string | null
  socketStatus: RelaySocketStatus
}

/**
 * RelayRoomPage가 URL의 roomCode로 호출하는 마스터 훅.
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
 *   - 드로잉/결과 단계의 PART_* 이벤트 처리 — 다음 wiring 단계에서 추가.
 */
export function useRelayRoom(roomCode: string | null): UseRelayRoomReturn {
  const router = useRouter()
  const storeRoomCode = useRelayDrawingStore((state) => state.roomCode)
  const hydrateRoomState = useRelayDrawingStore((state) => state.hydrateRoomState)
  const setRoomStatus = useRelayDrawingStore((state) => state.setRoomStatus)
  const setParticipants = useRelayDrawingStore((state) => state.setParticipants)
  const setHostUserUuid = useRelayDrawingStore((state) => state.setHostUserUuid)
  const setTimeLimitSeconds = useRelayDrawingStore((state) => state.setTimeLimitSeconds)
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom)

  // 부스에서 방 만들기 직후엔 store가 이미 같은 roomCode로 hydrate된 상태.
  // 추가 fetch가 끝날 때까지 굳이 로딩 UI를 띄울 필요가 없다.
  const isStoreSyncedToUrl = storeRoomCode === roomCode

  const [isFetching, setIsFetching] = useState(!isStoreSyncedToUrl)
  const [hydrationError, setHydrationError] = useState<string | null>(null)

  useEffect(() => {
    if (!roomCode) return

    let cancelled = false
    setIsFetching(true)
    setHydrationError(null)

    void (async () => {
      try {
        const room = await getRelayRoom(roomCode)
        if (cancelled) return
        hydrateRoomState(room)
      } catch (caughtError) {
        if (cancelled) return
        if (caughtError instanceof ApiError) {
          setHydrationError(caughtError.message)
        } else if (
          caughtError instanceof HTTPError &&
          caughtError.response.status === 404
        ) {
          setHydrationError('존재하지 않는 방이에요')
        } else {
          setHydrationError('방 정보를 가져오지 못했어요')
        }
      } finally {
        if (!cancelled) setIsFetching(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [roomCode, hydrateRoomState])

  // WebSocket 연결 — hydrate가 명시적으로 실패한 경우(잘못된 roomCode 등)에는
  // 굳이 connect를 시도하지 않는다.
  const { status: socketStatus } = useRelaySocket({
    roomCode,
    enabled: !hydrationError,
    handlers: {
      // ── 토픽: 방 전체 브로드캐스트 ───────────────────────────────
      PARTICIPANT_CONNECTED: (event) => {
        // 이벤트 data가 RoomState 스냅샷이라 hydrate로 한 번에 적용.
        hydrateRoomState({
          roomCode: event.data.roomCode,
          status: event.data.status,
          hostUserUuid: event.data.hostUserUuid,
          timeLimitSeconds: event.data.timeLimitSeconds,
          minParticipants: event.data.minParticipants,
          maxParticipants: event.data.maxParticipants,
          participants: event.data.participants,
        })
      },
      PARTICIPANT_DROPPED: (event) => {
        // 10초 grace 만료로 이탈 확정 — 현재 목록에서 제거.
        const current = useRelayDrawingStore.getState().participants
        setParticipants(
          current.filter((participant) => participant.userUuid !== event.data.userUuid),
        )
      },
      SETTINGS_CHANGED: (event) => {
        setTimeLimitSeconds(event.data.timeLimitSeconds)
        setParticipants(event.data.participants)
      },
      GAME_STARTED: (event) => {
        // roomStatus 'PLAYING'으로 전환 — RelayRoomPage가 자동으로 RelayDrawingView로 스왑.
        setRoomStatus(event.data.status)
        setParticipants(event.data.participants)
      },
      HOST_CHANGED: (event) => {
        setHostUserUuid(event.data.newHostUserUuid)
        // 참여자 목록의 host 플래그도 동기화.
        const current = useRelayDrawingStore.getState().participants
        setParticipants(
          current.map((participant) => ({
            ...participant,
            host: participant.userUuid === event.data.newHostUserUuid,
          })),
        )
      },
      ALL_PARTS_COMPLETED: (event) => {
        // FINALIZING으로 전환 → RelayRoomPage가 RelayFinalizingView 표시.
        setRoomStatus(event.data.roomStatus)
      },
      RESULT_CREATED: (event) => {
        // FINISHED로 전환 → RelayRoomPage가 RelayResultView 표시.
        // 결과 데이터 fetch(getRelayRoomResults)는 결과 wiring 단계에서 추가.
        setRoomStatus(event.data.roomStatus)
      },
      ROOM_CLOSED: (event) => {
        // 방 종료 — 모달은 후속 작업. 현재는 status만 갱신.
        setRoomStatus(event.data.roomStatus)
      },

      // ── 개인 큐: 본인에게만 전달되는 종료성 이벤트 ────────────────
      KICKED_FROM_ROOM: () => {
        // 강퇴 모달은 후속 작업. 지금은 룸 정리 후 부스로 복귀.
        clearRoom()
        router.push('/relay-drawing')
      },
      DUPLICATE_SESSION_CLOSED: () => {
        // 중복 세션 안내 모달도 후속. 같은 처리.
        clearRoom()
        router.push('/relay-drawing')
      },

      // PART_STARTED / PART_SUBMITTED / PART_AUTO_SUBMITTED는 드로잉 wiring에서.
    },
  })

  // store가 같은 roomCode로 동기화되어 있으면, 백그라운드 hydrate가 진행 중이어도
  // 사용자에게는 깜빡임 없이 화면을 보여준다.
  const isHydrating = isFetching && !isStoreSyncedToUrl

  return { isHydrating, hydrationError, socketStatus }
}
