import { api } from '@/shared/libs'
import type {
  ApiResponse,
  CommunityMemoCreateRequest,
  CommunityMemoLayoutRequest,
  CommunityMemoListResponse,
  CommunityMemoReportRequest,
  CommunityMemoReportResponse,
  CommunityMemoResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// GET /community/memos — 커뮤니티 메모 목록 조회
export const getCommunityMemoList = () =>
  apiUnwrap(api.get<ApiResponse<CommunityMemoListResponse>>('community/memos'))

// POST /community/memos — 커뮤니티 메모 생성
export const postCommunityMemo = (payload: CommunityMemoCreateRequest) =>
  apiUnwrap(api.post<ApiResponse<CommunityMemoResponse>>('community/memos', payload))

// GET /community/memos/{memoId} — 커뮤니티 메모 상세 조회
export const getCommunityMemo = (memoId: string) =>
  apiUnwrap(api.get<ApiResponse<CommunityMemoResponse>>(`community/memos/${memoId}`))

// PATCH /community/memos/{memoId} — 커뮤니티 메모 레이아웃 수정
export const patchCommunityMemo = (memoId: string, payload: CommunityMemoLayoutRequest) =>
  apiUnwrap(api.patch<ApiResponse<CommunityMemoResponse>>(`community/memos/${memoId}`, payload))

// DELETE /community/memos/{memoId} — 커뮤니티 메모 삭제
export const deleteCommunityMemo = (memoId: string) =>
  apiUnwrap(api.delete<ApiResponse<void>>(`community/memos/${memoId}`))

// POST /community/memos/{memoId}/reports — 커뮤니티 메모 신고
export const postCommunityMemoReport = (
  memoId: string,
  payload: CommunityMemoReportRequest,
) =>
  apiUnwrap(
    api.post<ApiResponse<CommunityMemoReportResponse>>(
      `community/memos/${memoId}/reports`,
      payload,
    ),
  )
