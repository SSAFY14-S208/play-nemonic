import { adminApi } from '@/shared/libs'
import type {
  ApiResponse,
  BackofficeRelayRoomDeleteResponse,
  BackofficeRelayRoomListResponse,
  BackofficeRoomListParams,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// GET /backoffice/relay-rooms — 활성 릴레이 방 목록 조회
export const getBackofficeRelayRoomList = (params?: BackofficeRoomListParams) =>
  apiUnwrap(
    adminApi.get<ApiResponse<BackofficeRelayRoomListResponse>>(
      'backoffice/relay-rooms',
      params as Record<string, string | number | boolean> | undefined,
    ),
  )

// DELETE /backoffice/relay-rooms/{roomCode} — 릴레이 방 삭제 (강제 종료)
export const deleteBackofficeRelayRoom = (roomCode: string) =>
  apiUnwrap(
    adminApi.delete<ApiResponse<BackofficeRelayRoomDeleteResponse>>(
      `backoffice/relay-rooms/${roomCode}`,
    ),
  )
