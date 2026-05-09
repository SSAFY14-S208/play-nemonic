import { api } from '@/shared/libs'
import type {
  ApiResponse,
  PhoneDrawingSaveRequest,
  PhoneDrawingSaveResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /gallery/drawings — 휴대폰 그림을 갤러리에 저장
// (artifact + phone_artifact + gallery row를 한 번에 생성)
export const postPhoneDrawing = (payload: PhoneDrawingSaveRequest) =>
  apiUnwrap(
    api.post<ApiResponse<PhoneDrawingSaveResponse>>('gallery/drawings', payload),
  )
