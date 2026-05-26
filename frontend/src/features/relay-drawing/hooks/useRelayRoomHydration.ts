'use client'

import { HTTPError } from 'ky'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import {
  ApiError,
  deleteRelayRoomParticipantMe,
  getRelayRoom,
  getRelayRoomResults,
  postInvite,
} from '@/shared/apis'
import { useUserStore } from '@/shared/stores'
import type { RelayBlockedReason, RelayRoomStatus } from '@/shared/types'

import { PART_TO_ROUND_KEY } from '@/features/relay-drawing/constants'
import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import { relayToast } from '@/features/relay-drawing/utils'
import { hasRoomDismissed, markRoomDismissed, resetRoomDismissed } from './relayRoomDismissal'

// viewer.blockedReason → 사용자 안내 토스트 메시지 매핑.
const BLOCKED_REASON_MESSAGE: Record<RelayBlockedReason, string> = {
  ROOM_FULL: '정원이 가득 찬 방이에요',
  GAME_IN_PROGRESS: '이미 게임이 진행 중인 방이에요',
  RECONNECT_EXPIRED: '재접속 시간이 만료되었어요',
  KICKED: '강퇴된 방이에요',
  ROOM_FINISHED: '이미 종료된 방이에요',
  ROOM_CLOSED: '종료된 방이에요',
}

function getNonParticipantBlockedReason(
  roomStatus: RelayRoomStatus,
): RelayBlockedReason | null {
  if (roomStatus === 'PLAYING' || roomStatus === 'FINALIZING') {
    return 'GAME_IN_PROGRESS'
  }
  if (roomStatus === 'FINISHED') {
    return 'ROOM_FINISHED'
  }
  if (roomStatus === 'CLOSED') {
    return 'ROOM_CLOSED'
  }
  return null
}

interface UseRelayRoomHydrationReturn {
  isFetching: boolean
  hydrationError: string | null
  isHydrating: boolean
  isViewerParticipant: boolean
}

/**
 * REST(getRelayRoom) hydration + 자발적 퇴장 감지를 담당한다.
 *
 * 책임:
 *   1. 마운트 시 getRelayRoom으로 방 상태를 hydrate한다.
 *   2. 비참여자 입장 차단, 자동 join, PLAYING/FINISHED 부가 fetch를 처리한다.
 *   3. 페이지 이탈 시 deleteRelayRoomParticipantMe를 호출해 자발적 퇴장을 서버에 알린다.
 *   4. isViewerParticipant를 계산해 WS 연결 게이트로 반환한다.
 */
export function useRelayRoomHydration(
  roomCode: string | null,
): UseRelayRoomHydrationReturn {
  const router = useRouter()
  const currentUserUuid = useUserStore((state) => state.userUuid)
  const storeRoomCode = useRelayDrawingStore((state) => state.roomCode)
  const participants = useRelayDrawingStore((state) => state.participants)
  const hydrateRoomState = useRelayDrawingStore((state) => state.hydrateRoomState)

  // 부스에서 방 만들기 직후엔 store가 이미 같은 roomCode로 hydrate된 상태.
  // 추가 fetch가 끝날 때까지 굳이 로딩 UI를 띄울 필요가 없다.
  const isStoreSyncedToUrl = storeRoomCode === roomCode

  const [isFetching, setIsFetching] = useState(!isStoreSyncedToUrl)
  const [hydrationError, setHydrationError] = useState<string | null>(null)

  useEffect(() => {
    if (!roomCode) return

    let cancelled = false

    void (async () => {
      setIsFetching(true)
      setHydrationError(null)
      try {
        const room = await getRelayRoom(roomCode)
        if (cancelled) return

        // ── 비참여자 입장 차단 ──
        // 참여자가 아니면서 이미 시작/종료된 방이거나 입장도 불가능한 경우
        // store를 hydrate하지 않고 부스로 즉시 복귀한다.
        const statusBlockedReason = getNonParticipantBlockedReason(room.status)
        if (
          !room.viewer.participant &&
          (statusBlockedReason !== null || !room.viewer.canJoin)
        ) {
          const blockedReason = statusBlockedReason ?? room.viewer.blockedReason
          markRoomDismissed(roomCode)
          relayToast(
            BLOCKED_REASON_MESSAGE[blockedReason] ?? '입장할 수 없는 방이에요',
          )
          router.replace('/relay-drawing')
          return
        }

        hydrateRoomState(room)

        // 공유 링크 진입 자동 join — 가이드 §11.
        // 본인이 아직 참여자가 아니고 입장 가능한 WAITING 상태면 자동으로
        // postInvite를 호출해 백엔드 participants 목록에 등록한 뒤,
        // getRelayRoom으로 방 상태를 다시 가져와 store를 갱신한다.
        if (
          room.status === 'WAITING' &&
          room.viewer.canJoin &&
          !room.viewer.participant
        ) {
          try {
            await postInvite(roomCode)
            if (cancelled) return
            const joined = await getRelayRoom(roomCode)
            if (cancelled) return
            hydrateRoomState(joined)
          } catch (joinError) {
            if (cancelled) return
            const message =
              joinError instanceof ApiError
                ? joinError.message
                : '방 입장에 실패했어요'
            setHydrationError(message)
            return
          }
        }

        // PLAYING 상태에서 새로고침 시 deadline을 즉시 반영 — 타이머가 정확한 남은 시간으로 시작한다.
        if (room.status === 'PLAYING') {
          useRelayDrawingStore.getState().setPartDeadlineAt(room.partDeadlineAt)
          // 라운드별 데드라인 세팅 — 새로고침 복귀 시에도 auto-submit 게이트가 열리도록.
          const roundKey = PART_TO_ROUND_KEY[room.currentPart]
          useRelayDrawingStore
            .getState()
            .setRoundDeadline(roundKey, room.partDeadlineAt)
          // WS GAME_STARTED가 먼저 도착해 trigger를 이미 올렸으면 건너뛴다.
          if (useRelayDrawingStore.getState().partFetchTrigger === 0) {
            useRelayDrawingStore.getState().incrementPartFetchTrigger()
          }
        }

        // 새로고침/직접 URL 진입 시 이미 FINISHED면 결과도 함께 가져온다.
        if (room.status === 'FINISHED') {
          try {
            const resultResponse = await getRelayRoomResults(roomCode)
            if (!cancelled) {
              useRelayDrawingStore.getState().setResults(resultResponse.results)
            }
          } catch {
            // 결과 fetch 실패 시 view에서 빈 상태로 처리.
          }
        }
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
  }, [roomCode, hydrateRoomState, router])

  // ── 자발적 퇴장 감지 ─────────────────────────────────────────
  // 페이지 이탈(브라우저 뒤로가기·라우트 전환 등) 시 서버에 퇴장 의사를 즉시
  // 전달한다. WS 종료성 이벤트(강퇴·방 종료 등)로 인한 퇴장은 이미 서버가
  // 처리했으므로 중복 호출을 방지한다.
  useEffect(() => {
    if (!roomCode) return
    resetRoomDismissed(roomCode)

    return () => {
      if (!hasRoomDismissed(roomCode)) {
        void deleteRelayRoomParticipantMe(roomCode).catch(() => {})
      }
      resetRoomDismissed(roomCode)
    }
  }, [roomCode])

  // WebSocket 연결 게이트 — REST hydrate 완료 + 본인이 백엔드 participant 목록에
  // 등록된 시점에만 WS connect를 시도한다(직접 링크 진입 race 방지).
  const isViewerParticipant =
    currentUserUuid !== null &&
    storeRoomCode === roomCode &&
    participants.some(
      (participant) => participant.userUuid === currentUserUuid,
    )

  // store가 같은 roomCode로 동기화되어 있으면, 백그라운드 hydrate가 진행 중이어도
  // 사용자에게는 깜빡임 없이 화면을 보여준다.
  const isHydrating = isFetching && !isStoreSyncedToUrl

  return { isFetching, hydrationError, isHydrating, isViewerParticipant }
}
