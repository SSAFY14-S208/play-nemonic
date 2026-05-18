import { adminApi } from '@/shared/libs'
import type {
  ApiResponse,
  BackofficeInfiniteCanvasCloseResponse,
  BackofficeInfiniteCanvasListParams,
  BackofficeInfiniteCanvasListResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// GET /backoffice/infinite-canvas/canvases — 활성 무한 캔버스 목록 조회
export const getBackofficeInfiniteCanvasList = (
  params?: BackofficeInfiniteCanvasListParams,
) =>
  apiUnwrap(
    adminApi.get<ApiResponse<BackofficeInfiniteCanvasListResponse>>(
      'backoffice/infinite-canvas/canvases',
      params as Record<string, string | number | boolean> | undefined,
    ),
  )

// DELETE /backoffice/infinite-canvas/canvases/{roomCode} — 활성 무한 캔버스 강제 종료
export const deleteBackofficeInfiniteCanvas = (roomCode: string) =>
  apiUnwrap(
    adminApi.delete<ApiResponse<BackofficeInfiniteCanvasCloseResponse>>(
      `backoffice/infinite-canvas/canvases/${roomCode}`,
    ),
  )
