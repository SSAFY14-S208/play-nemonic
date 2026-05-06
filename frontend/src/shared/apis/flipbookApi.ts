import { api } from '@/shared/libs'
import type {
  ApiResponse,
  FlipbookRoomCreateResponse,
  FlipbookRoomStateResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /flipbook/rooms — 플립북 방 생성
export const postFlipbookRoom = (userUuid: string) =>
  apiUnwrap(api.post<ApiResponse<FlipbookRoomCreateResponse>>('flipbook/rooms', { userUuid }))

// PATCH /flipbook/rooms/{roomCode}/settings — 플립북 방 설정 변경
export const patchFlipbookRoomSettings = (
  roomCode: string,
  userUuid: string,
  timeLimitSeconds: number,
) =>
  apiUnwrap(
    api.patch<ApiResponse<FlipbookRoomStateResponse>>(`flipbook/rooms/${roomCode}/settings`, {
      userUuid,
      timeLimitSeconds,
    }),
  )

// GET /flipbook/rooms/{roomCode} — 플립북 대기실 정보 조회
export const getFlipbookRoom = (roomCode: string, userUuid: string) =>
  apiUnwrap(
    api.get<ApiResponse<FlipbookRoomStateResponse>>(`flipbook/rooms/${roomCode}`, { userUuid }),
  )
