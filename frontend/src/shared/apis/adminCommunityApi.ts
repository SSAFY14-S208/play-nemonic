import { adminApi } from '@/shared/libs'
import type {
  AdminCommunityMemoDetailResponse,
  AdminCommunityMemoListParams,
  AdminCommunityMemoListResponse,
  AdminCommunityMemoReportListParams,
  AdminCommunityMemoReportListResponse,
  AdminCommunityMemoResponse,
  AdminCommunityMemoReviewRequest,
  ApiResponse,
} from '@/shared/types'

import { apiUnwrap, normalizeOcrCategories } from '@/shared/utils'

// 백엔드는 `ocrCategories`를 JSON 문자열 또는 배열로 내려준다. UI 단에서 분기하지 않도록
// API 함수가 응답을 받자마자 항상 `string[]`로 정규화한다.
function normalizeListItem<T extends AdminCommunityMemoResponse>(item: T): T {
  return { ...item, ocrCategories: normalizeOcrCategories(item.ocrCategories) }
}

// GET /admin/community/memos — 관리자 커뮤니티 메모 목록 조회
export const getAdminCommunityMemoList = async (
  params?: AdminCommunityMemoListParams,
): Promise<AdminCommunityMemoListResponse> => {
  const response = await apiUnwrap(
    adminApi.get<ApiResponse<AdminCommunityMemoListResponse>>(
      'admin/community/memos',
      params as Record<string, string | number | boolean> | undefined,
    ),
  )
  return { ...response, items: response.items.map(normalizeListItem) }
}

// GET /admin/community/memos/{memoId} — 관리자 커뮤니티 메모 상세 조회
// 응답에 decoration 객체와 신고 내역(reports[])이 포함된다.
export const getAdminCommunityMemo = async (
  memoId: string,
): Promise<AdminCommunityMemoDetailResponse> => {
  const detail = await apiUnwrap(
    adminApi.get<ApiResponse<AdminCommunityMemoDetailResponse>>(
      `admin/community/memos/${memoId}`,
    ),
  )
  return normalizeListItem(detail)
}

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
export const patchAdminCommunityMemoHide = async (
  memoId: string,
  payload: AdminCommunityMemoReviewRequest,
): Promise<AdminCommunityMemoDetailResponse> => {
  const detail = await apiUnwrap(
    adminApi.patch<ApiResponse<AdminCommunityMemoDetailResponse>>(
      `admin/community/memos/${memoId}/hide`,
      payload,
    ),
  )
  return normalizeListItem(detail)
}

// PATCH /admin/community/memos/{memoId}/restore — 메모 복원 처리
export const patchAdminCommunityMemoRestore = async (
  memoId: string,
  payload: AdminCommunityMemoReviewRequest,
): Promise<AdminCommunityMemoDetailResponse> => {
  const detail = await apiUnwrap(
    adminApi.patch<ApiResponse<AdminCommunityMemoDetailResponse>>(
      `admin/community/memos/${memoId}/restore`,
      payload,
    ),
  )
  return normalizeListItem(detail)
}
