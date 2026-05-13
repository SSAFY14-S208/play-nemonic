import { api } from '@/shared/libs'
import type { ApiResponse, InquiryCreateRequest, InquiryCreateResponse } from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /inquiries — CS 문의 생성
export const postInquiry = (payload: InquiryCreateRequest) =>
  apiUnwrap(api.post<ApiResponse<InquiryCreateResponse>>('inquiries', payload))
