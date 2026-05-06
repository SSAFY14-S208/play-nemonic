'use client'

import { useState, useTransition } from 'react'

import { ApiError, patchRelayRoomSettings, postRelayRoomStart } from '@/shared/apis'
import { useUserStore } from '@/shared/stores'

import { useRelayDrawingStore } from '../stores'

interface UseRelayLobbyReturn {
  // 현재 사용자가 호스트인지. 시작 버튼/시간 설정/강퇴 등 호스트 전용 UI 게이트.
  isHost: boolean
  // 게임 시작 가능 조건: 호스트 + 진행 중 아님 + 최소 인원 + 모두 connected.
  canStartGame: boolean
  startError: string | null
  isStarting: boolean

  settingsError: string | null
  isUpdatingSettings: boolean

  // "링크 복사" 클릭 직후 잠깐 true — UI에서 "복사됨" 토스트 표시용.
  copyConfirm: boolean

  startGame: () => void
  changeTimeLimit: (seconds: number) => void
  copyInviteLink: () => void
  clearErrors: () => void
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
  const userUuid = useUserStore((state) => state.userUuid)
  const roomCode = useRelayDrawingStore((state) => state.roomCode)
  const hostUserUuid = useRelayDrawingStore((state) => state.hostUserUuid)
  const participants = useRelayDrawingStore((state) => state.participants)
  const minParticipants = useRelayDrawingStore((state) => state.minParticipants)
  const setTimeLimitSeconds = useRelayDrawingStore((state) => state.setTimeLimitSeconds)

  const [isStarting, startStartTransition] = useTransition()
  const [startError, setStartError] = useState<string | null>(null)

  const [isUpdatingSettings, startSettingsTransition] = useTransition()
  const [settingsError, setSettingsError] = useState<string | null>(null)

  const [copyConfirm, setCopyConfirm] = useState(false)

  const isHost = userUuid !== null && userUuid === hostUserUuid
  // 가이드 §15: "최소 2명 이상, 모든 참여자가 WebSocket 연결 상태여야 한다"
  const allConnected =
    participants.length > 0 && participants.every((participant) => participant.connected)
  const canStartGame =
    isHost && !isStarting && participants.length >= minParticipants && allConnected

  const startGame = () => {
    if (!roomCode || !canStartGame) return
    setStartError(null)
    startStartTransition(async () => {
      try {
        await postRelayRoomStart(roomCode)
        // GAME_STARTED 이벤트가 곧 도착해 store.roomStatus를 'PLAYING'으로 바꾼다.
        // RelayRoomPage가 이를 감지해 자동으로 RelayDrawingView로 스왑.
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '게임 시작에 실패했어요'
        setStartError(message)
      }
    })
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

  const copyInviteLink = () => {
    if (typeof window === 'undefined') return
    void navigator.clipboard.writeText(window.location.href).then(() => {
      setCopyConfirm(true)
      window.setTimeout(() => setCopyConfirm(false), COPY_CONFIRM_DURATION_MS)
    })
  }

  const clearErrors = () => {
    setStartError(null)
    setSettingsError(null)
  }

  return {
    isHost,
    canStartGame,
    startError,
    isStarting,
    settingsError,
    isUpdatingSettings,
    copyConfirm,
    startGame,
    changeTimeLimit,
    copyInviteLink,
    clearErrors,
  }
}
