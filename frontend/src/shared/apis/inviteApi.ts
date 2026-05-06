import { api } from '@/shared/libs'
import type { ApiResponse, InviteJoinResponse } from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /invites/{inviteCode} — 초대코드로 협동 부스 입장
export const postInvite = (inviteCode: string) =>
  apiUnwrap(api.post<ApiResponse<InviteJoinResponse>>(`invites/${inviteCode}`))
