import { api } from '@/shared/libs'
import type { ApiResponse, CommunityDetailResponse } from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// GET /community/{communityId} — 커뮤니티 상세 조회
export const getCommunity = (communityId: number) =>
  apiUnwrap(api.get<ApiResponse<CommunityDetailResponse>>(`community/${communityId}`))
