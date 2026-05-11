import { adminApi } from '@/shared/libs'
import type {
  AdminInquiryListParams,
  AdminInquiryListResponse,
  AdminInquiryReplyRequest,
  AdminInquiryReplyResponse,
  AdminInquiryResponse,
  AdminInquiryStatusRequest,
  AdminInquiryStatusResponse,
  ApiResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// GET /admin/inquiries — 관리자 문의 목록 조회
export const getAdminInquiryList = (params?: AdminInquiryListParams) =>
  apiUnwrap(
    adminApi.get<ApiResponse<AdminInquiryListResponse>>(
      'admin/inquiries',
      params as Record<string, string | number | boolean> | undefined,
    ),
  )

// GET /admin/inquiries/{inquiryId} — 관리자 문의 상세 조회
export const getAdminInquiry = (inquiryId: string) =>
  apiUnwrap(
    adminApi.get<ApiResponse<AdminInquiryResponse>>(`admin/inquiries/${inquiryId}`),
  )

// POST /admin/inquiries/{inquiryId}/reply — 문의 답변
export const postAdminInquiryReply = (
  inquiryId: string,
  payload: AdminInquiryReplyRequest,
) =>
  apiUnwrap(
    adminApi.post<ApiResponse<AdminInquiryReplyResponse>>(
      `admin/inquiries/${inquiryId}/reply`,
      payload,
    ),
  )

// PATCH /admin/inquiries/{inquiryId}/status — 문의 상태 변경
export const patchAdminInquiryStatus = (
  inquiryId: string,
  payload: AdminInquiryStatusRequest,
) =>
  apiUnwrap(
    adminApi.patch<ApiResponse<AdminInquiryStatusResponse>>(
      `admin/inquiries/${inquiryId}/status`,
      payload,
    ),
  )
