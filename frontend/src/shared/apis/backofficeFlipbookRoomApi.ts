import { adminApi } from '@/shared/libs'
import type {
  ApiResponse,
  BackofficeFlipbookRoomDeleteResponse,
  BackofficeFlipbookRoomListResponse,
  BackofficeRoomListParams,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// GET /backoffice/flipbook-rooms — 활성 플립북 방 목록 조회
export const getBackofficeFlipbookRoomList = (params?: BackofficeRoomListParams) =>
  apiUnwrap(
    adminApi.get<ApiResponse<BackofficeFlipbookRoomListResponse>>(
      'backoffice/flipbook-rooms',
      params as Record<string, string | number | boolean> | undefined,
    ),
  )

// DELETE /backoffice/flipbook-rooms/{roomCode} — 플립북 방 삭제 (강제 종료)
export const deleteBackofficeFlipbookRoom = (roomCode: string) =>
  apiUnwrap(
    adminApi.delete<ApiResponse<BackofficeFlipbookRoomDeleteResponse>>(
      `backoffice/flipbook-rooms/${roomCode}`,
    ),
  )
