'use client'

import { HTTPError } from 'ky'
import { useRouter } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'

import {
  ApiError,
  deleteRelayRoomParticipantMe,
  getRelayRoom,
  getRelayRoomResults,
  postRelayRoomParticipant,
} from '@/shared/apis'
import type { RelaySocketStatus } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'

import { PART_TO_ROUND_KEY } from '../constants'
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
  const currentUserUuid = useUserStore((state) => state.userUuid)
  const storeRoomCode = useRelayDrawingStore((state) => state.roomCode)
  const participants = useRelayDrawingStore((state) => state.participants)
  const hydrateRoomState = useRelayDrawingStore((state) => state.hydrateRoomState)
  const setRoomStatus = useRelayDrawingStore((state) => state.setRoomStatus)
  const setParticipants = useRelayDrawingStore((state) => state.setParticipants)
  const setHostUserUuid = useRelayDrawingStore((state) => state.setHostUserUuid)
  const setTimeLimitSeconds = useRelayDrawingStore((state) => state.setTimeLimitSeconds)
  const setDismissalReason = useRelayDrawingStore((state) => state.setDismissalReason)
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom)

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
        hydrateRoomState(room)

        // 공유 링크 진입 자동 join — 가이드 §11.
        // 본인이 아직 참여자가 아니고 입장 가능한 WAITING 상태면 자동으로
        // postRelayRoomParticipant를 호출해 백엔드 participants 목록에 등록한다.
        // 이미 참여자(부스에서 방 만들기/입장 직후 또는 새로고침)인 경우는 건너뛴다.
        // PLAYING/FINISHED/CLOSED 상태에서는 신규 입장 불가하므로 시도하지 않는다.
        if (
          room.status === 'WAITING' &&
          room.viewer.canJoin &&
          !room.viewer.participant
        ) {
          try {
            const joined = await postRelayRoomParticipant(roomCode)
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
          useRelayDrawingStore.getState().setRoundDeadline(roundKey, room.partDeadlineAt)
          // WS GAME_STARTED가 먼저 도착해 trigger를 이미 올렸으면 건너뛴다.
          // 중복 increment는 진행 중인 assignment fetch의 retry를 취소시켜서
          // 서버 배정 생성 시간만큼의 retry 윈도우를 낭비한다.
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
  }, [roomCode, hydrateRoomState])

  // ── 자발적 퇴장 감지 ─────────────────────────────────────────
  // 페이지 이탈(브라우저 뒤로가기·라우트 전환 등) 시 서버에 퇴장 의사를 즉시
  // 전달한다. WS 종료성 이벤트(강퇴·방 종료 등)로 인한 퇴장은 이미 서버가
  // 처리했으므로 중복 호출을 방지한다.
  const wasDismissedRef = useRef(false)

  useEffect(() => {
    if (!roomCode) return
    wasDismissedRef.current = false

    return () => {
      if (!wasDismissedRef.current) {
        void deleteRelayRoomParticipantMe(roomCode).catch(() => {})
      }
    }
  }, [roomCode])

  // WebSocket 연결 — REST hydrate 완료 + 본인이 백엔드 participant 목록에 등록된
  // 시점에만 connect를 시도한다. 직접 링크 진입 시 REST chain(getRelayRoom +
  // 자동 postRelayRoomParticipant)이 끝나기 전에 STOMP CONNECT가 먼저 발사되면
  // 백엔드가 "허용할 수 없습니다" 에러로 거부하고, 5초 후 재연결로 복구되는
  // race가 있어서 이 게이트를 둔다.
  //
  // 부스 입장 플로우는 이미 postRelayRoomParticipant 후 navigate하므로 첫 렌더에
  // 본인이 participants에 들어있어 즉시 enabled=true가 된다. 새로고침 케이스도
  // REST 응답이 본인을 포함한 채 오면 동일.
  const isViewerParticipant =
    currentUserUuid !== null &&
    storeRoomCode === roomCode &&
    participants.some((participant) => participant.userUuid === currentUserUuid)

  const { status: socketStatus } = useRelaySocket({
    roomCode,
    enabled: !hydrationError && isViewerParticipant,
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

        // CONNECTED 이벤트의 대상 참여자는 반드시 connected: true여야 한다.
        // 서버 스냅샷 타이밍에 따라 false로 내려올 수 있으므로 명시적으로 보정한다.
        const connectedUuid = event.data.changedParticipant.userUuid
        const current = useRelayDrawingStore.getState().participants
        setParticipants(
          current.map((participant) =>
            participant.userUuid === connectedUuid
              ? { ...participant, connected: true }
              : participant,
          ),
        )

        // 다른 사용자가 입장한 경우에만 토스트 — 본인 입장은 알림 불필요.
        const currentUserUuid = useUserStore.getState().userUuid
        if (connectedUuid !== currentUserUuid) {
          toast(`${event.data.changedParticipant.nickname}님이 입장했습니다.`)
        }
      },
      PARTICIPANT_DISCONNECTED: (event) => {
        // WS 끊김 — 참여자의 connected 플래그만 false로 갱신.
        // WAITING 상태에서는 유예 없이 재접속 가능, PLAYING에서는 10초 유예 후
        // PARTICIPANT_DROPPED가 별도로 온다.
        const current = useRelayDrawingStore.getState().participants
        setParticipants(
          current.map((participant) =>
            participant.userUuid === event.data.userUuid
              ? { ...participant, connected: false }
              : participant,
          ),
        )
      },
      // 임시 워크어라운드 — 백엔드가 PARTICIPANT_DISCONNECTED 응답에서 퇴장 유저를
      // 제거하면 이 핸들러는 삭제 예정. 현재는 DISCONNECTED가 퇴장 유저를 포함한
      // 채로 오기 때문에, LEFT 이벤트에서 해당 유저를 명시적으로 필터링한다.
      PARTICIPANT_LEFT: (event) => {
        const current = useRelayDrawingStore.getState().participants
        setParticipants(
          current.filter((participant) => participant.userUuid !== event.data.leftUserUuid),
        )
        toast(`${event.data.leftNickname}님이 방을 나갔습니다.`)
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
        useRelayDrawingStore.getState().setIsSubmitting(false)
        // 첫 파트 deadline을 즉시 반영 — 타이머가 정확한 남은 시간으로 시작한다.
        useRelayDrawingStore.getState().setPartDeadlineAt(event.data.partDeadlineAt)
        // 라운드별 데드라인 세팅 — auto-submit이 이 라운드의 데드라인 수신을 확인할 수 있게.
        const roundKey = PART_TO_ROUND_KEY[event.data.currentPart]
        useRelayDrawingStore.getState().setRoundDeadline(roundKey, event.data.partDeadlineAt)
        // effect 트리거 — fetch가 currentPart/canvasIndex/hint를 채운다.
        useRelayDrawingStore.getState().incrementPartFetchTrigger()
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
        setRoomStatus(event.data.roomStatus)
        // WS 이벤트에는 drawer 정보가 없으므로 REST로 full data를 가져온다.
        const currentRoomCode = useRelayDrawingStore.getState().roomCode
        if (currentRoomCode) {
          void (async () => {
            try {
              const response = await getRelayRoomResults(currentRoomCode)
              useRelayDrawingStore.getState().setResults(response.results)
            } catch {
              // fetch 실패 시 빈 결과로 표시 — 재시도는 후속.
            }
          })()
        }
      },
      ROOM_CLOSED: () => {
        // 방 종료 — 모달로 안내 후 부스 복귀. roomStatus는 건드리지 않아서
        // 현재 뷰 위에 모달이 오버레이된다.
        wasDismissedRef.current = true
        setDismissalReason('ROOM_CLOSED')
      },
      // 호스트가 다른 참여자를 강퇴 — 방 전체 브로드캐스트.
      // 본인이 강퇴 대상이면 KICKED_FROM_ROOM(개인 큐)을 기다리지 않고 여기서 즉시
      // 부스로 복귀시킨다. 두 이벤트의 도착 순서가 보장되지 않고, 브로드캐스트가
      // 먼저 오는 경우도 있어 양쪽 모두에서 처리하되 wasDismissedRef로 dedup한다.
      PARTICIPANT_KICKED: (event) => {
        const currentUserUuid = useUserStore.getState().userUuid

        if (currentUserUuid === event.data.kickedUserUuid) {
          if (wasDismissedRef.current) return
          wasDismissedRef.current = true
          toast.error('호스트에 의해 방에서 내보내졌습니다.')
          clearRoom()
          router.push('/relay-drawing')
          return
        }

        // 다른 사람이 강퇴 — 남아있는 참여자 목록에서 제거 + 토스트.
        const current = useRelayDrawingStore.getState().participants
        setParticipants(
          current.filter((participant) => participant.userUuid !== event.data.kickedUserUuid),
        )
        toast(`${event.data.kickedNickname}님이 강퇴되었습니다.`)
      },

      // ── 개인 큐: 본인에게만 전달되는 종료성 이벤트 ────────────────
      // 강퇴 대상자: 모달로 멈추지 않고 즉시 부스로 복귀시키고 토스트로 사유를 알린다.
      // PARTICIPANT_KICKED 브로드캐스트가 먼저 도착해 이미 처리됐으면 dedup으로 건너뛴다.
      KICKED_FROM_ROOM: () => {
        if (wasDismissedRef.current) return
        wasDismissedRef.current = true
        toast.error('호스트에 의해 방에서 내보내졌습니다.')
        clearRoom()
        router.push('/relay-drawing')
      },
      DUPLICATE_SESSION_CLOSED: () => {
        wasDismissedRef.current = true
        setDismissalReason('DUPLICATE_SESSION')
      },

      // ── 드로잉 진행 이벤트 ──────────────────────────────────────
      PART_STARTED: (event) => {
        // 새 파트 시작 — 이전 파트 제출이 모두 완료된 뒤 서버가 보낸다.
        // deadline과 timeLimitSeconds를 store에 반영한다. 실제 배정(canvasIndex,
        // hint, currentPart)은 useRelayDrawingGame이 getRelayRoomAssignmentMe로 가져온다.
        setTimeLimitSeconds(event.data.timeLimitSeconds)
        useRelayDrawingStore.getState().setPartDeadlineAt(event.data.partDeadlineAt)
        // 라운드별 데드라인 세팅 — 새 라운드의 auto-submit 게이트 해제.
        const roundKey = PART_TO_ROUND_KEY[event.data.part]
        useRelayDrawingStore.getState().setRoundDeadline(roundKey, event.data.partDeadlineAt)
        // effect 트리거 — partDeadlineAt 대신 전용 카운터 사용
        useRelayDrawingStore.getState().incrementPartFetchTrigger()
      },
      PART_SUBMITTED: (event) => {
        // 다른 참여자가 제출 — 진행도 갱신 (e.g. "2/3 제출 완료").
        useRelayDrawingStore.getState().updateSubmissionProgress(
          event.data.submittedCount,
          event.data.totalCount,
        )
      },
      PART_AUTO_SUBMITTED: () => {
        // 서버 자동 제출 (유예기간 2초 내 미제출). 이벤트 data에 submittedCount/totalCount가
        // 없으므로 진행도 갱신 불가. 서버가 이어서 PART_STARTED 또는 ALL_PARTS_COMPLETED를
        // 보내므로 여기서는 추가 처리 불필요.
      },
    },
  })

  // STOMP 연결 확립 시 본인의 connected 플래그를 즉시 true로 보정.
  // REST hydration은 WS 연결 전에 완료되므로 participants가 connected: false로
  // 내려오고, PARTICIPANT_CONNECTED 이벤트와 store 등록 시점이 어긋나면 그 상태가
  // 남는다. socketStatus가 'connected'로 전환되면 실제 연결이 살아있으므로 즉시 반영.
  useEffect(() => {
    if (socketStatus !== 'connected') return
    const currentUserUuid = useUserStore.getState().userUuid
    if (!currentUserUuid) return
    const current = useRelayDrawingStore.getState().participants
    const me = current.find((participant) => participant.userUuid === currentUserUuid)
    if (!me || me.connected) return
    setParticipants(
      current.map((participant) =>
        participant.userUuid === currentUserUuid
          ? { ...participant, connected: true }
          : participant,
      ),
    )
  }, [socketStatus, setParticipants])

  // store가 같은 roomCode로 동기화되어 있으면, 백그라운드 hydrate가 진행 중이어도
  // 사용자에게는 깜빡임 없이 화면을 보여준다.
  const isHydrating = isFetching && !isStoreSyncedToUrl

  return { isHydrating, hydrationError, socketStatus }
}
