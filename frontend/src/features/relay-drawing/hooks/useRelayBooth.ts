'use client'

import { useRouter } from 'next/navigation'
import { useState, useTransition } from 'react'

import { ApiError, getRelayRoom, postInvite, postRelayRoom } from '@/shared/apis'
import { DEFAULT_USER_NICKNAME } from '@/shared/constants'
import { useFunnelEntry } from '@/shared/hooks'
import { completeFunnelStep } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'

import { useRelayDrawingStore } from '../stores'

interface UseRelayBoothReturn {
  // UserBootstrap이 끝나 userUuid가 발급된 상태인지. 부트스트랩 진행 중일 때
  // 컴포넌트가 버튼을 비활성화하는 데 쓴다.
  isUserReady: boolean
  // 닉네임이 기본값('익명')이거나 비어 있으면 true — 가이드 §9에 따라 백엔드가
  // 거부하므로, 컴포넌트가 닉네임 모달로 분기시키는 신호로 사용한다.
  needsNicknameSetup: boolean
  isPending: boolean
  error: string | null

  createRoom: () => void
  joinRoom: (roomCode: string) => void
  clearError: () => void
}

/**
 * 부스 화면의 "방 만들기" / "방 입장" 액션을 결선한다.
 *
 * 흐름:
 *   1. 방 만들기: POST /relay/rooms
 *      방 입장: POST /invites/{roomCode} → GET /relay/rooms/{roomId}
 *   2. 성공 응답을 store.hydrateRoomState로 넣어 RelayRoomPage가 로비를
 *      바로 그릴 수 있도록 준비
 *   3. router.push(`/relay-drawing/${roomCode}`) — RelayRoomPage 마운트
 *
 * RelayRoomPage 자체는 마운트 시 getRelayRoom으로 다시 hydrate해 stale 상태를
 * 보정하므로, 여기서의 hydrate는 화면 깜빡임을 줄이는 즉시 채움 용도.
 */
export function useRelayBooth(): UseRelayBoothReturn {
  const router = useRouter()
  const userUuid = useUserStore((state) => state.userUuid)
  const nickname = useUserStore((state) => state.nickname)
  const hydrateRoomState = useRelayDrawingStore((state) => state.hydrateRoomState)

  // 부스 마운트 = relay funnel landing (step_index=0). startFunnel이 step 1 진입을
  // 의미하므로 별도 logFunnelStep 호출은 하지 않는다.
  useFunnelEntry('relay_room_creation')

  const [isPending, startTransition] = useTransition()
  const [error, setError] = useState<string | null>(null)

  const isUserReady = userUuid !== null
  // 가이드 §9 — 닉네임이 기본값('익명')이거나 비어 있으면 백엔드가 방 생성을 거부.
  // 컴포넌트는 이 신호를 보고 닉네임 모달로 분기시킨다.
  const needsNicknameSetup =
    !nickname || nickname.trim() === '' || nickname === DEFAULT_USER_NICKNAME

  const navigateToRoom = (roomCode: string) => {
    router.push(`/relay-drawing/${roomCode}`)
  }

  const createRoom = () => {
    // 가드 — 컴포넌트가 needsNicknameSetup을 먼저 분기시켜야 하지만, 직접 호출
    // 시에도 안전하도록 hook 레벨에서도 한 번 더 차단한다.
    if (!isUserReady || isPending || needsNicknameSetup) return
    setError(null)
    startTransition(async () => {
      try {
        const room = await postRelayRoom()
        hydrateRoomState(room)
        // 닉네임은 이미 게이트를 통과했고, 방 생성 성공 = settings 단계까지 완료한 것.
        completeFunnelStep('nickname', 1, { content_type: 'relay' })
        completeFunnelStep('settings', 2, {
          content_type: 'relay',
          room_id: room.roomCode,
        })
        navigateToRoom(room.roomCode)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '방 생성에 실패했어요'
        setError(message)
      }
    })
  }

  const joinRoom = (rawRoomCode: string) => {
    if (!isUserReady || isPending || needsNicknameSetup) return
    const roomCode = rawRoomCode.trim().toUpperCase()
    if (!roomCode) {
      setError('방 코드를 입력해주세요')
      return
    }
    setError(null)
    startTransition(async () => {
      try {
        const invite = await postInvite(roomCode)
        const room = await getRelayRoom(invite.roomId)
        hydrateRoomState(room)
        completeFunnelStep('nickname', 1, { content_type: 'relay' })
        completeFunnelStep('settings', 2, {
          content_type: 'relay',
          room_id: invite.roomId,
        })
        navigateToRoom(invite.roomId)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '방 입장에 실패했어요'
        setError(message)
      }
    })
  }

  const clearError = () => setError(null)

  return {
    isUserReady,
    needsNicknameSetup,
    isPending,
    error,
    createRoom,
    joinRoom,
    clearError,
  }
}
