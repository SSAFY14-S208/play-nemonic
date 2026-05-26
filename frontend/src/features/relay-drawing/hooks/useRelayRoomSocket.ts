'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { getRelayRoomResults } from '@/shared/apis'
import { completeFunnelStep, type RelaySocketStatus } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'

import { PART_TO_ROUND_KEY } from '@/features/relay-drawing/constants'
import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import { relayToast } from '@/features/relay-drawing/utils'
import { hasRoomDismissed, markRoomDismissed } from './relayRoomDismissal'
import { useRelaySocket } from './useRelaySocket'

interface UseRelayRoomSocketParams {
  roomCode: string | null
  enabled: boolean
}

interface UseRelayRoomSocketReturn {
  socketStatus: RelaySocketStatus
}

/**
 * WebSocket 연결 + 이벤트 → store 라우팅을 담당한다.
 *
 * 책임:
 *   1. useRelaySocket을 통해 STOMP 연결을 관리한다.
 *   2. 방 전체 브로드캐스트 이벤트(참여자 입출, 게임 진행 등)를 수신해 store를 갱신한다.
 *   3. 개인 큐 이벤트(강퇴, 중복 세션)를 수신해 룸 정리 + 부스 이동을 처리한다.
 *   4. STOMP 연결 확립 시 본인의 connected 플래그를 즉시 true로 보정한다.
 */
export function useRelayRoomSocket({
  roomCode,
  enabled,
}: UseRelayRoomSocketParams): UseRelayRoomSocketReturn {
  const router = useRouter()
  const setRoomStatus = useRelayDrawingStore((state) => state.setRoomStatus)
  const setParticipants = useRelayDrawingStore((state) => state.setParticipants)
  const setHostUserUuid = useRelayDrawingStore((state) => state.setHostUserUuid)
  const setTimeLimitSeconds = useRelayDrawingStore(
    (state) => state.setTimeLimitSeconds,
  )
  const setDismissalReason = useRelayDrawingStore(
    (state) => state.setDismissalReason,
  )
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom)
  const hydrateRoomState = useRelayDrawingStore((state) => state.hydrateRoomState)

  const { status: socketStatus } = useRelaySocket({
    roomCode,
    enabled,
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
          relayToast(`${event.data.changedParticipant.nickname}님이 입장했습니다.`)
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
      PARTICIPANT_LEFT: (event) => {
        const current = useRelayDrawingStore.getState().participants
        setParticipants(
          current.filter(
            (participant) => participant.userUuid !== event.data.leftUserUuid,
          ),
        )
        relayToast(`${event.data.leftNickname}님이 방을 나갔습니다.`)
      },
      PARTICIPANT_DROPPED: (event) => {
        // 10초 grace 만료로 이탈 확정 — 현재 목록에서 제거.
        const current = useRelayDrawingStore.getState().participants
        setParticipants(
          current.filter(
            (participant) => participant.userUuid !== event.data.userUuid,
          ),
        )
      },
      SETTINGS_CHANGED: (event) => {
        setTimeLimitSeconds(event.data.timeLimitSeconds)
        setParticipants(event.data.participants)
      },
      GAME_STARTED: (event) => {
        // 게임 시작 애니메이션 — 호스트는 버튼 클릭 시 이미 'animating'이므로 no-op,
        // 비호스트는 여기서 'animating'으로 전환해 로비 패널 슬라이드 아웃 +
        // 게임 시작 이미지를 보여준다. RelayRoomPage의 effect가 일정 시간 후
        // 'idle'로 되돌리면 RelayDrawingView로 자연스럽게 전환된다.
        //
        // funnel: lobby step 완료는 방장의 startGame()에서도 emit되지만 게스트는
        // 그 함수를 호출하지 않아 lobby 잔존 카운트에서 빠진다. WS GAME_STARTED는
        // 방장·게스트 모두에게 broadcast되므로 여기서 한 번 더 emit해 게스트의
        // funnel_step_completed 누락을 메운다. distinct uuid 집계라 방장의 이중
        // emit은 카운트에 영향 없음.
        completeFunnelStep('lobby', 3, {
          content_type: 'relay',
          room_id: roomCode,
        })
        useRelayDrawingStore.getState().setGameStartPhase('animating')
        // roomStatus 'PLAYING'으로 전환.
        setRoomStatus(event.data.status)
        setParticipants(event.data.participants)
        useRelayDrawingStore.getState().setIsSubmitting(false)
        // 첫 파트 deadline을 즉시 반영 — 타이머가 정확한 남은 시간으로 시작한다.
        useRelayDrawingStore
          .getState()
          .setPartDeadlineAt(event.data.partDeadlineAt)
        // 라운드별 데드라인 세팅 — auto-submit이 이 라운드의 데드라인 수신을 확인할 수 있게.
        const roundKey = PART_TO_ROUND_KEY[event.data.currentPart]
        useRelayDrawingStore
          .getState()
          .setRoundDeadline(roundKey, event.data.partDeadlineAt)
        // effect 트리거 — fetch가 currentPart/canvasIndex/hint를 채운다.
        useRelayDrawingStore.getState().incrementPartFetchTrigger()
        // 새 파트라 제출자 목록도 비운다.
        useRelayDrawingStore.getState().clearSubmittedUserUuids()
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
        // 방장 변경 토스트 — 본인이면 임명 안내, 타인이면 닉네임 표시.
        const currentUserUuid = useUserStore.getState().userUuid
        if (event.data.newHostUserUuid === currentUserUuid) {
          relayToast('방장으로 임명되었습니다.')
        } else {
          relayToast(`${event.data.newHostNickname}님이 방장으로 임명되었습니다.`)
        }
      },
      ALL_PARTS_COMPLETED: (event) => {
        // FINALIZING으로 전환 → RelayRoomPage가 RelayFinalizingView 표시.
        setRoomStatus(event.data.roomStatus)
        // 마지막 라운드의 PART_TIME_UP 오버레이는 여기서 내린다.
        // (다음 PART_STARTED가 오지 않으므로 자연 소멸 경로가 없음.)
        useRelayDrawingStore.getState().setPartTimeUp(false)
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
        markRoomDismissed(roomCode)
        setDismissalReason('ROOM_CLOSED')
      },
      // 호스트가 다른 참여자를 강퇴 — 방 전체 브로드캐스트.
      // 본인이 강퇴 대상이면 KICKED_FROM_ROOM(개인 큐)을 기다리지 않고 여기서 즉시
      // 부스로 복귀시킨다. 두 이벤트의 도착 순서가 보장되지 않고, 브로드캐스트가
      // 먼저 오는 경우도 있어 양쪽 모두에서 처리하되 dismissal dedup으로 중복을 막는다.
      PARTICIPANT_KICKED: (event) => {
        const currentUserUuid = useUserStore.getState().userUuid

        if (currentUserUuid === event.data.kickedUserUuid) {
          if (hasRoomDismissed(roomCode)) return
          markRoomDismissed(roomCode)
          relayToast.error('호스트에 의해 방에서 내보내졌습니다.')
          clearRoom()
          router.push('/relay-drawing')
          return
        }

        // 다른 사람이 강퇴 — 남아있는 참여자 목록에서 제거 + 토스트.
        const current = useRelayDrawingStore.getState().participants
        setParticipants(
          current.filter(
            (participant) => participant.userUuid !== event.data.kickedUserUuid,
          ),
        )
        relayToast(`${event.data.kickedNickname}님이 강퇴되었습니다.`)
      },

      // ── 개인 큐: 본인에게만 전달되는 종료성 이벤트 ────────────────
      // 강퇴 대상자: 모달로 멈추지 않고 즉시 부스로 복귀시키고 토스트로 사유를 알린다.
      // PARTICIPANT_KICKED 브로드캐스트가 먼저 도착해 이미 처리됐으면 dedup으로 건너뛴다.
      KICKED_FROM_ROOM: () => {
        if (hasRoomDismissed(roomCode)) return
        markRoomDismissed(roomCode)
        relayToast.error('호스트에 의해 방에서 내보내졌습니다.')
        clearRoom()
        router.push('/relay-drawing')
      },
      DUPLICATE_SESSION_CLOSED: () => {
        markRoomDismissed(roomCode)
        setDismissalReason('DUPLICATE_SESSION')
      },

      // ── 드로잉 진행 이벤트 ──────────────────────────────────────
      PART_STARTED: (event) => {
        // 새 파트 시작 — 이전 파트 제출이 모두 완료된 뒤 서버가 보낸다.
        // deadline과 timeLimitSeconds를 store에 반영한다. 실제 배정(canvasIndex,
        // hint, currentPart)은 useRelayDrawingGame이 getRelayRoomAssignmentMe로 가져온다.
        setTimeLimitSeconds(event.data.timeLimitSeconds)
        useRelayDrawingStore
          .getState()
          .setPartDeadlineAt(event.data.partDeadlineAt)
        // 라운드별 데드라인 세팅 — 새 라운드의 auto-submit 게이트 해제.
        const roundKey = PART_TO_ROUND_KEY[event.data.part]
        useRelayDrawingStore
          .getState()
          .setRoundDeadline(roundKey, event.data.partDeadlineAt)
        // effect 트리거 — partDeadlineAt 대신 전용 카운터 사용
        useRelayDrawingStore.getState().incrementPartFetchTrigger()
        // 새 파트 시작 — 이전 파트의 제출자 목록은 더 이상 의미 없음.
        useRelayDrawingStore.getState().clearSubmittedUserUuids()
        // 이전 파트의 PART_TIME_UP 오버레이를 즉시 내린다.
        // setAssignment에서도 false로 리셋되지만, 새 배정 fetch가 도착하기 전에
        // 사용자가 다음 라운드 시작 신호를 받았다는 신호를 즉시 보여주기 위함.
        useRelayDrawingStore.getState().setPartTimeUp(false)
      },
      PART_TIME_UP: (event) => {
        // 데드라인 도달 — 백엔드가 미제출자에게 자동 제출을 지시한다.
        // 본인이 pendingSubmissions에 포함되어 있고 아직 미제출이면 즉시 자동 제출 트리거.
        // 그렇지 않으면 오버레이만 띄우고 PART_STARTED를 기다린다(가이드: 백엔드가
        // 모든 in-flight 제출을 처리한 뒤에야 다음 PART_STARTED 발사).
        useRelayDrawingStore.getState().setPartTimeUp(true)

        const currentUserUuid = useUserStore.getState().userUuid
        if (!currentUserUuid) return
        const isMePending = event.data.pendingSubmissions.some(
          (pending) => pending.userUuid === currentUserUuid,
        )
        if (!isMePending) return

        const roundKey = PART_TO_ROUND_KEY[event.data.part]
        // 같은 라운드에서 이미 제출 완료된 상태면 자동 제출 안 함.
        // (PART_TIME_UP보다 본인 제출 응답이 살짝 빨리 도달한 케이스 안전망.)
        if (useRelayDrawingStore.getState().roundSubmitted[roundKey]) return

        useRelayDrawingStore.getState().triggerPendingAutoSubmit()
      },
      PART_SUBMITTED: (event) => {
        // 다른 참여자가 제출 — 진행도 + 제출자 UUID 갱신.
        // RoundProgressPanel이 submittedUserUuids로 "X님 완료" 표시를 띄운다.
        useRelayDrawingStore
          .getState()
          .updateSubmissionProgress(
            event.data.submittedCount,
            event.data.totalCount,
          )
        useRelayDrawingStore
          .getState()
          .addSubmittedUserUuid(event.data.userUuid)
      },
      PART_AUTO_SUBMITTED: (event) => {
        // 서버 자동 제출 (유예기간 2초 내 미제출). submittedCount/totalCount는
        // 안 오지만 어떤 사용자가 자동 제출됐는지는 알 수 있어 친구 패널 표시에 반영.
        useRelayDrawingStore
          .getState()
          .addSubmittedUserUuid(event.data.userUuid)
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
    const me = current.find(
      (participant) => participant.userUuid === currentUserUuid,
    )
    if (!me || me.connected) return
    setParticipants(
      current.map((participant) =>
        participant.userUuid === currentUserUuid
          ? { ...participant, connected: true }
          : participant,
      ),
    )
  }, [socketStatus, setParticipants])

  return { socketStatus }
}
