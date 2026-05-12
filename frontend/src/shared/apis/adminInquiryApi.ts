import { adminApi } from '@/shared/libs'
import type {
  AdminInquiryDetailResponse,
  AdminInquiryListParams,
  AdminInquiryListResponse,
  AdminInquiryReplyRequest,
  AdminInquiryReplyResponse,
  AdminInquiryStatusRequest,
  AdminInquiryStatusUpdateResponse,
  ApiResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// path param 타입은 Swagger상 string이지만 응답의 id는 number — 호출 측 편의를 위해 둘 다 수용.
type InquiryId = number | string

// GET /admin/inquiries — 관리자 문의 목록 조회
export const getAdminInquiryList = (params?: AdminInquiryListParams) =>
  apiUnwrap(
    adminApi.get<ApiResponse<AdminInquiryListResponse>>(
      'admin/inquiries',
      params as Record<string, string | number | boolean> | undefined,
    ),
  )

// GET /admin/inquiries/{inquiryId} — 관리자 문의 상세 조회
// 응답에 content/attachments/meta + 운영자 후속 처리(assignedTo/responseNote/respondedAt) 포함.
export const getAdminInquiry = (inquiryId: InquiryId) =>
  apiUnwrap(
    adminApi.get<ApiResponse<AdminInquiryDetailResponse>>(
      `admin/inquiries/${inquiryId}`,
    ),
  )

// POST /admin/inquiries/{inquiryId}/reply — 문의 이메일 회신 (응답은 부분 갱신값)
export const postAdminInquiryReply = (
  inquiryId: InquiryId,
  payload: AdminInquiryReplyRequest,
) =>
  apiUnwrap(
    adminApi.post<ApiResponse<AdminInquiryReplyResponse>>(
      `admin/inquiries/${inquiryId}/reply`,
      payload,
    ),
  )

// PATCH /admin/inquiries/{inquiryId}/status — 문의 상태 변경 (응답은 부분 갱신값)
export const patchAdminInquiryStatus = (
  inquiryId: InquiryId,
  payload: AdminInquiryStatusRequest,
) =>
  apiUnwrap(
    adminApi.patch<ApiResponse<AdminInquiryStatusUpdateResponse>>(
      `admin/inquiries/${inquiryId}/status`,
      payload,
    ),
  )
