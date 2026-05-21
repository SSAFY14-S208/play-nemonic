import { api } from '@/shared/libs'
import type {
  ApiResponse,
  FortuneAvailabilityResponse,
  FortuneCreateRequest,
  FortuneCreateResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

export const getFortuneTodayAvailability = () =>
  apiUnwrap(api.get<ApiResponse<FortuneAvailabilityResponse>>('fortune/today/availability'))

export const postFortune = (payload: FortuneCreateRequest) =>
  apiUnwrap(api.post<ApiResponse<FortuneCreateResponse>>('fortune', payload))

// GET /fortune/today — 오늘의 운세 조회
export const getFortuneToday = () =>
  apiUnwrap(api.get<ApiResponse<FortuneCreateResponse>>('fortune/today'))
