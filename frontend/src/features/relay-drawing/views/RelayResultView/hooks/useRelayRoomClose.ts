'use client'

import { useState } from 'react'

import { ApiError, postRelayRoomClose } from '@/shared/apis'

interface UseRelayRoomCloseParams {
  roomCode: string | null
  isHost: boolean
}

interface UseRelayRoomCloseReturn {
  isClosingRoom: boolean
  closeRoomError: string | null
  closeRoom: () => void
}

/**
 * 호스트 전용 방 종료 액션.
 *
 * 성공 시 ROOM_CLOSED WS 이벤트가 도착해 dismissalReason이 세팅되고
 * RelayDismissalModal이 자동으로 안내한다. store를 직접 건드리지 않는다.
 */
export function useRelayRoomClose({
  roomCode,
  isHost,
}: UseRelayRoomCloseParams): UseRelayRoomCloseReturn {
  const [isClosingRoom, setIsClosingRoom] = useState(false)
  const [closeRoomError, setCloseRoomError] = useState<string | null>(null)

  const closeRoom = () => {
    if (!roomCode || !isHost || isClosingRoom) return
    setCloseRoomError(null)
    setIsClosingRoom(true)
    void (async () => {
      try {
        await postRelayRoomClose(roomCode)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError ? caughtError.message : '방 종료에 실패했어요'
        setCloseRoomError(message)
      } finally {
        setIsClosingRoom(false)
      }
    })()
  }

  return { isClosingRoom, closeRoomError, closeRoom }
}
