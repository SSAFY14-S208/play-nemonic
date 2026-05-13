import { adminApi } from '@/shared/libs'
import type {
  ApiResponse,
  SystemParameterBulkUpdateRequest,
  SystemParameterListParams,
  SystemParameterListResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// GET /backoffice/system-parameters — 시스템 파라미터 조회 ({ items, totalElements })
export const getSystemParameters = (params?: SystemParameterListParams) =>
  apiUnwrap(
    adminApi.get<ApiResponse<SystemParameterListResponse>>(
      'backoffice/system-parameters',
      params as Record<string, string | number | boolean> | undefined,
    ),
  )

// PATCH /backoffice/system-parameters — 시스템 파라미터 일괄 수정
export const patchSystemParameters = (payload: SystemParameterBulkUpdateRequest) =>
  apiUnwrap(
    adminApi.patch<ApiResponse<SystemParameterListResponse>>(
      'backoffice/system-parameters',
      payload,
    ),
  )
