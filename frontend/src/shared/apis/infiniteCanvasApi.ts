import { api } from '@/shared/libs'
import type {
  ApiResponse,
  InfiniteCanvasCreateRequest,
  InfiniteCanvasLeaveResponse,
  InfiniteCanvasOutputSaveRequest,
  InfiniteCanvasOutputSaveResponse,
  InfiniteCanvasParticipantResponse,
  InfiniteCanvasParticipantUpdateRequest,
  InfiniteCanvasStateResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /infinite-canvas/canvases — 무한 캔버스 생성
export const postInfiniteCanvas = (payload?: InfiniteCanvasCreateRequest) =>
  apiUnwrap(api.post<ApiResponse<InfiniteCanvasStateResponse>>('infinite-canvas/canvases', payload))

export const postInfiniteCanvasCanvas = postInfiniteCanvas

// GET /infinite-canvas/canvases/{canvasId} — 무한 캔버스 상태 조회/참여
export const getInfiniteCanvas = (canvasId: string) =>
  apiUnwrap(api.get<ApiResponse<InfiniteCanvasStateResponse>>(`infinite-canvas/canvases/${canvasId}`))

export const getInfiniteCanvasCanvas = getInfiniteCanvas

// PATCH /infinite-canvas/canvases/{canvasId}/participants/me — 내 참여자 정보 수정
export const patchInfiniteCanvasParticipantMe = (
  canvasId: string,
  payload: InfiniteCanvasParticipantUpdateRequest,
) =>
  apiUnwrap(
    api.patch<ApiResponse<InfiniteCanvasParticipantResponse>>(
      `infinite-canvas/canvases/${canvasId}/participants/me`,
      payload,
    ),
  )

// DELETE /infinite-canvas/canvases/{canvasId}/participants/me — 무한 캔버스 퇴장
export const deleteInfiniteCanvasParticipantMe = (canvasId: string) =>
  apiUnwrap(
    api.delete<ApiResponse<InfiniteCanvasLeaveResponse>>(
      `infinite-canvas/canvases/${canvasId}/participants/me`,
    ),
  )

// POST /infinite-canvas/canvases/{canvasId}/outputs — 출력 이미지 저장
export const postInfiniteCanvasOutput = (
  canvasId: string,
  payload: InfiniteCanvasOutputSaveRequest,
) =>
  apiUnwrap(
    api.post<ApiResponse<InfiniteCanvasOutputSaveResponse>>(
      `infinite-canvas/canvases/${canvasId}/outputs`,
      payload,
    ),
  )
