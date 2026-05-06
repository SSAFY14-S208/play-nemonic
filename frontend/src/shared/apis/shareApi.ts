import { api } from '@/shared/libs'
import type { ApiResponse, ShareCreateRequest, ShareCreateResponse } from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /share — SNS 공유 정보 생성
export const postShare = (payload: ShareCreateRequest) =>
  apiUnwrap(api.post<ApiResponse<ShareCreateResponse>>('share', payload))
