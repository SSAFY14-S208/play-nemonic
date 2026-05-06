import { adminApi, api } from '@/shared/libs'
import type { ApiResponse, LoginRequest, LoginResponse } from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /auth/login — 관리자 로그인 (토큰 없이 호출하므로 일반 client 사용)
export const postLogin = (payload: LoginRequest) =>
  apiUnwrap(api.post<ApiResponse<LoginResponse>>('auth/login', payload))

// POST /auth/logout — 관리자 로그아웃 (Bearer 필요 → adminApi)
export const postLogout = (refreshToken: string) =>
  apiUnwrap(adminApi.post<ApiResponse<void>>('auth/logout', { refreshToken }))

// POST /auth/reissue — 관리자 토큰 재발급
// 토큰이 만료된 상태에서도 호출되므로 일반 client 사용 (Authorization 헤더 없음).
// adminApiClient 내부의 401 retry 인터셉터가 이 엔드포인트를 별도 호출하므로, 도메인
// 함수 자체는 일반 client로 두어 인터셉터 재귀를 방지한다.
export const postReissue = (refreshToken: string) =>
  apiUnwrap(api.post<ApiResponse<LoginResponse>>('auth/reissue', { refreshToken }))
