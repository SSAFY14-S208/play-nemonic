import { adminApi } from '@/shared/libs'
import type {
  AdminCreateRequest,
  AdminPasswordChangeRequest,
  AdminResponse,
  ApiResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// GET /admins — 관리자 계정 목록 조회
export const getAdminList = () =>
  apiUnwrap(adminApi.get<ApiResponse<AdminResponse[]>>('admins'))

// POST /admins — 관리자 계정 생성
export const postAdmin = (payload: AdminCreateRequest) =>
  apiUnwrap(adminApi.post<ApiResponse<AdminResponse>>('admins', payload))

// GET /admins/{adminId} — 관리자 계정 상세 조회
export const getAdmin = (adminId: number) =>
  apiUnwrap(adminApi.get<ApiResponse<AdminResponse>>(`admins/${adminId}`))

// PATCH /admins/{adminId} — 관리자 비밀번호 변경
export const patchAdminPassword = (adminId: number, payload: AdminPasswordChangeRequest) =>
  apiUnwrap(adminApi.patch<ApiResponse<void>>(`admins/${adminId}`, payload))

// DELETE /admins/{adminId} — 관리자 계정 삭제
export const deleteAdmin = (adminId: number) =>
  apiUnwrap(adminApi.delete<ApiResponse<void>>(`admins/${adminId}`))
