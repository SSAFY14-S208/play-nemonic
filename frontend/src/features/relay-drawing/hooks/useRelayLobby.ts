'use client'

import { useState, useTransition } from 'react'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'

import {
  ApiError,
  patchRelayRoomSettings,
  postRelayRoomKick,
} from '@/shared/apis'
import { completeFunnelStep } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'

import { useRelayDrawingStore } from '../stores'

interface UseRelayLobbyReturn {
  // 현재 사용자가 호스트인지. 시작 버튼/시간 설정/강퇴 등 호스트 전용 UI 게이트.
  isHost: boolean
  // 게임 시작 가능 조건: 호스트 + 진행 중 아님 + 최소 인원 + 모두 connected.
  canStartGame: boolean
  isStarting: boolean

  settingsError: string | null
  isUpdatingSettings: boolean

  // 강퇴 진행 중인 대상자의 UUID. 같은 타일의 X 버튼을 비활성화하는 데 쓴다.
  kickingTargetUuid: string | null
  kickError: string | null

  // 어느 복사 버튼이 방금 눌렸는지 — 같은 카드의 두 버튼("링크 복사"·"입장 코드
  // 복사")이 각각 자기 라벨만 "복사됨"으로 바꿀 수 있게 enum으로 둔다.
  // null이면 어느 쪽도 최근에 복사되지 않은 상태.
  copyConfirm: 'link' | 'roomCode' | null

  startGame: () => void
  changeTimeLimit: (seconds: number) => void
  kickParticipant: (targetUserUuid: string) => void
  copyInviteLink: () => void
  copyRoomCode: () => void
  clearErrors: () => void
  leaveRoom: () => void
}

const COPY_CONFIRM_DURATION_MS = 2000

/**
 * 로비 화면의 호스트 전용 액션 묶음.
 *
 * 시작/설정 변경 모두 백엔드가 진실의 출처이므로, 응답 후 우리가 store를 직접
 * 갱신하지 않는다. 시작은 GAME_STARTED WS 이벤트가, 설정 변경은 SETTINGS_CHANGED
 * WS 이벤트가 store를 갱신한다(가이드 §25).
 *
 * 단 설정 변경은 클릭 즉시 UI에 반영하는 게 자연스러워 optimistic 업데이트를 쓰고,
 * 백엔드가 거부했을 때만 이전 값으로 명시적으로 롤백한다.
 */
export function useRelayLobby(): UseRelayLobbyReturn {
  const router = useRouter()
  const userUuid = useUserStore((state) => state.userUuid)
  const roomCode = useRelayDrawingStore((state) => state.roomCode)
  const hostUserUuid = useRelayDrawingStore((state) => state.hostUserUuid)
  const participants = useRelayDrawingStore((state) => state.participants)
  const minParticipants = useRelayDrawingStore((state) => state.minParticipants)
  const setTimeLimitSeconds = useRelayDrawingStore((state) => state.setTimeLimitSeconds)
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom)

  const gameStartPhase = useRelayDrawingStore((state) => state.gameStartPhase)
  const setGameStartPhase = useRelayDrawingStore((state) => state.setGameStartPhase)

  const [isUpdatingSettings, startSettingsTransition] = useTransition()
  const [settingsError, setSettingsError] = useState<string | null>(null)

  const [kickingTargetUuid, setKickingTargetUuid] = useState<string | null>(null)
  const [kickError, setKickError] = useState<string | null>(null)

  const [copyConfirm, setCopyConfirm] = useState<'link' | 'roomCode' | null>(null)

  const isHost = userUuid !== null && userUuid === hostUserUuid
  const isStarting = gameStartPhase === 'animating'
  // 가이드 §15: "최소 2명 이상, 모든 참여자가 WebSocket 연결 상태여야 한다"
  const allConnected =
    participants.length > 0 && participants.every((participant) => participant.connected)
  const canStartGame =
    isHost && !isStarting && participants.length >= minParticipants && allConnected

  // 게임 시작 — API를 즉시 호출하지 않고 gameStartPhase를 'animating'으로 전환.
  // 로비 패널 슬라이드 아웃 + 게임 시작 이미지 등장 애니메이션 후에
  // RelayRoomPage가 실제 API(postRelayRoomStart)를 발사한다.
  const startGame = () => {
    if (!roomCode || !canStartGame) return
    completeFunnelStep('lobby', 3, {
      content_type: 'relay',
      room_id: roomCode,
      participant_count: participants.length,
    })
    setGameStartPhase('animating')
  }

  const changeTimeLimit = (seconds: number) => {
    if (!roomCode || !isHost || isUpdatingSettings) return
    // Optimistic — 즉시 UI 반영. 실패 시 이전 값으로 명시 롤백.
    const previous = useRelayDrawingStore.getState().timeLimitSeconds
    if (previous === seconds) return
    setTimeLimitSeconds(seconds)
    setSettingsError(null)
    startSettingsTransition(async () => {
      try {
        await patchRelayRoomSettings(roomCode, seconds)
        // 성공 — SETTINGS_CHANGED 이벤트가 곧 도착해 같은 값으로 다시 set 됨(no-op).
      } catch (caughtError) {
        setTimeLimitSeconds(previous)
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '설정 변경에 실패했어요'
        setSettingsError(message)
      }
    })
  }

  // 호스트가 다른 참여자를 강퇴 — 가이드 §13.
  // 성공 시 PARTICIPANT_LEFT/KICKED_FROM_ROOM WS 이벤트로 자연 갱신되므로
  // 응답 데이터를 직접 store에 반영하지 않는다.
  const kickParticipant = (targetUserUuid: string) => {
    if (!roomCode || !isHost || kickingTargetUuid) return
    if (targetUserUuid === userUuid) return
    setKickError(null)
    setKickingTargetUuid(targetUserUuid)
    void (async () => {
      try {
        await postRelayRoomKick(roomCode, targetUserUuid)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '참여자 강퇴에 실패했어요'
        setKickError(message)
      } finally {
        setKickingTargetUuid(null)
      }
    })()
  }

  // 전역 Toaster는 top-center 기본값이지만 복사 피드백은 클릭 위치(하단 카드)
  // 가까이 띄우는 게 자연스러워 이 두 호출만 bottom-center로 위치 override.
  const copyInviteLink = () => {
    if (typeof window === 'undefined') return
    void navigator.clipboard.writeText(window.location.href).then(() => {
      setCopyConfirm('link')
      window.setTimeout(() => setCopyConfirm(null), COPY_CONFIRM_DURATION_MS)
      toast.success('초대 링크를 복사했어요', { position: 'bottom-center' })
    })
  }

  const copyRoomCode = () => {
    if (typeof window === 'undefined' || !roomCode) return
    void navigator.clipboard.writeText(roomCode).then(() => {
      setCopyConfirm('roomCode')
      window.setTimeout(() => setCopyConfirm(null), COPY_CONFIRM_DURATION_MS)
      toast.success(`입장 코드 ${roomCode}를 복사했어요`, {
        position: 'bottom-center',
      })
    })
  }

  const clearErrors = () => {
    setSettingsError(null)
    setKickError(null)
  }

  // 자발적 퇴장 — clearRoom()으로 store를 비우고 부스로 이동한다.
  // 실제 서버 퇴장 API(deleteRelayRoomParticipantMe)는 useRelayRoom의
  // cleanup effect가 roomCode 변경을 감지해 자동으로 호출한다.
  const leaveRoom = () => {
    clearRoom()
    router.push('/relay-drawing')
  }

  return {
    isHost,
    canStartGame,
    isStarting,
    settingsError,
    isUpdatingSettings,
    kickingTargetUuid,
    kickError,
    copyConfirm,
    startGame,
    changeTimeLimit,
    kickParticipant,
    copyInviteLink,
    copyRoomCode,
    clearErrors,
    leaveRoom,
  }
}
