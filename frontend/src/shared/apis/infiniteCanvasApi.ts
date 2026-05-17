import { api } from '@/shared/libs'
import type {
  ApiResponse,
  InfiniteCanvasCreateResponse,
  InfiniteCanvasCreateRequest,
  InfiniteCanvasLeaveResponse,
  InfiniteCanvasOutputSaveRequest,
  InfiniteCanvasOutputSaveResponse,
  InfiniteCanvasParticipantProfileUpdateRequest,
  InfiniteCanvasParticipantResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /infinite-canvas/canvases — 무한 캔버스 생성
export const postInfiniteCanvas = (payload?: InfiniteCanvasCreateRequest) =>
  apiUnwrap(api.post<ApiResponse<InfiniteCanvasCreateResponse>>('infinite-canvas/canvases', payload))

export const postInfiniteCanvasCanvas = postInfiniteCanvas

// PATCH /infinite-canvas/canvases/{roomCode}/participants/me — 무한 캔버스 내 참여자 색상 수정
export const patchInfiniteCanvasParticipantProfile = (
  roomCode: string,
  payload: InfiniteCanvasParticipantProfileUpdateRequest,
) =>
  apiUnwrap(
    api.patch<ApiResponse<InfiniteCanvasParticipantResponse>>(
      `infinite-canvas/canvases/${roomCode}/participants/me`,
      payload,
    ),
  )

// DELETE /infinite-canvas/canvases/{roomCode}/participants/me — 무한 캔버스 퇴장
export const deleteInfiniteCanvasParticipantMe = (roomCode: string) =>
  apiUnwrap(
    api.delete<ApiResponse<InfiniteCanvasLeaveResponse>>(
      `infinite-canvas/canvases/${roomCode}/participants/me`,
    ),
  )

// POST /infinite-canvas/canvases/{roomCode}/outputs — 출력 이미지 저장
export const postInfiniteCanvasOutput = (
  roomCode: string,
  payload: InfiniteCanvasOutputSaveRequest,
) =>
  apiUnwrap(
    api.post<ApiResponse<InfiniteCanvasOutputSaveResponse>>(
      `infinite-canvas/canvases/${roomCode}/outputs`,
      payload,
    ),
  )
