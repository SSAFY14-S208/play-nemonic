import { adminApi } from '@/shared/libs'
import type {
  AdminCommunityMemoListParams,
  AdminCommunityMemoListResponse,
  AdminCommunityMemoReportListParams,
  AdminCommunityMemoReportListResponse,
  AdminCommunityMemoResponse,
  AdminCommunityMemoReviewRequest,
  ApiResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// GET /admin/community/memos — 관리자 커뮤니티 메모 목록 조회
export const getAdminCommunityMemoList = (params?: AdminCommunityMemoListParams) =>
  apiUnwrap(
    adminApi.get<ApiResponse<AdminCommunityMemoListResponse>>(
      'admin/community/memos',
      params as Record<string, string | number | boolean> | undefined,
    ),
  )

// GET /admin/community/memos/{memoId} — 관리자 커뮤니티 메모 상세 조회
export const getAdminCommunityMemo = (memoId: string) =>
  apiUnwrap(
    adminApi.get<ApiResponse<AdminCommunityMemoResponse>>(`admin/community/memos/${memoId}`),
  )

// GET /admin/community/memos/{memoId}/reports — 메모 신고 내역 조회
export const getAdminCommunityMemoReports = (
  memoId: string,
  params?: AdminCommunityMemoReportListParams,
) =>
  apiUnwrap(
    adminApi.get<ApiResponse<AdminCommunityMemoReportListResponse>>(
      `admin/community/memos/${memoId}/reports`,
      params as Record<string, string | number | boolean> | undefined,
    ),
  )

// PATCH /admin/community/memos/{memoId}/hide — 메모 숨김 처리
export const patchAdminCommunityMemoHide = (
  memoId: string,
  payload: AdminCommunityMemoReviewRequest,
) =>
  apiUnwrap(
    adminApi.patch<ApiResponse<AdminCommunityMemoResponse>>(
      `admin/community/memos/${memoId}/hide`,
      payload,
    ),
  )

// PATCH /admin/community/memos/{memoId}/restore — 메모 복원 처리
export const patchAdminCommunityMemoRestore = (
  memoId: string,
  payload: AdminCommunityMemoReviewRequest,
) =>
  apiUnwrap(
    adminApi.patch<ApiResponse<AdminCommunityMemoResponse>>(
      `admin/community/memos/${memoId}/restore`,
      payload,
    ),
  )
