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
