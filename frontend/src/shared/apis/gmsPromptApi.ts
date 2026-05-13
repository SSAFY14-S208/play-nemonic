import { adminApi } from '@/shared/libs'
import type {
  ApiResponse,
  GmsPromptCreateRequest,
  GmsPromptListParams,
  GmsPromptListResponse,
  GmsPromptResponse,
  GmsPromptUpdateRequest,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// GET /backoffice/gms/prompts — GMS 프롬프트 목록 조회
export const getGmsPromptList = (params?: GmsPromptListParams) =>
  apiUnwrap(
    adminApi.get<ApiResponse<GmsPromptListResponse>>(
      'backoffice/gms/prompts',
      params as Record<string, string | number | boolean> | undefined,
    ),
  )

// POST /backoffice/gms/prompts — GMS 프롬프트 생성
export const postGmsPrompt = (payload: GmsPromptCreateRequest) =>
  apiUnwrap(
    adminApi.post<ApiResponse<GmsPromptResponse>>('backoffice/gms/prompts', payload),
  )

// GET /backoffice/gms/prompts/{promptId} — GMS 프롬프트 상세 조회
export const getGmsPrompt = (promptId: number) =>
  apiUnwrap(
    adminApi.get<ApiResponse<GmsPromptResponse>>(`backoffice/gms/prompts/${promptId}`),
  )

// PATCH /backoffice/gms/prompts/{promptId} — GMS 프롬프트 수정
export const patchGmsPrompt = (promptId: number, payload: GmsPromptUpdateRequest) =>
  apiUnwrap(
    adminApi.patch<ApiResponse<GmsPromptResponse>>(
      `backoffice/gms/prompts/${promptId}`,
      payload,
    ),
  )

// DELETE /backoffice/gms/prompts/{promptId} — GMS 프롬프트 삭제
export const deleteGmsPrompt = (promptId: number) =>
  apiUnwrap(
    adminApi.delete<ApiResponse<void>>(`backoffice/gms/prompts/${promptId}`),
  )
