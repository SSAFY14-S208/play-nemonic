import { api } from '@/shared/libs'
import type {
  AnonymousUserBirthInfoRequest,
  AnonymousUserBirthInfoResponse,
  AnonymousUserNicknameRequest,
  AnonymousUserNicknameResponse,
  AnonymousUserProfileResponse,
  AnonymousUserResponse,
  AnonymousUserVerifyResponse,
  ApiResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// userUuid는 apiClient의 beforeRequest 훅이 useUserStore에서 읽어
// Anonymous-User-UUID 헤더로 자동 주입한다. 도메인 함수는 헤더를 직접 다루지 않는다.

// POST /users/anonymous — 익명 사용자 UUID 발급 (호출 시점에는 store가 비어있어 헤더 미주입)
export const postAnonymous = () =>
  apiUnwrap(api.post<ApiResponse<AnonymousUserResponse>>('users/anonymous'))

// POST /users/anonymous/verify — 익명 사용자 UUID 확인
export const postAnonymousVerify = () =>
  apiUnwrap(api.post<ApiResponse<AnonymousUserVerifyResponse>>('users/anonymous/verify'))

// POST /users/anonymous/birth-info — 익명 사용자 생년월일 정보 등록
export const postAnonymousBirthInfo = (payload: AnonymousUserBirthInfoRequest) =>
  apiUnwrap(
    api.post<ApiResponse<AnonymousUserBirthInfoResponse>>('users/anonymous/birth-info', payload),
  )

// PATCH /users/anonymous/birth-info — 익명 사용자 생년월일 정보 수정
export const patchAnonymousBirthInfo = (payload: AnonymousUserBirthInfoRequest) =>
  apiUnwrap(
    api.patch<ApiResponse<AnonymousUserBirthInfoResponse>>('users/anonymous/birth-info', payload),
  )

// PATCH /users/anonymous/nickname — 익명 사용자 닉네임 설정/수정
export const patchAnonymousNickname = (payload: AnonymousUserNicknameRequest) =>
  apiUnwrap(
    api.patch<ApiResponse<AnonymousUserNicknameResponse>>('users/anonymous/nickname', payload),
  )

// GET /users/anonymous/profile — 익명 사용자 프로필 조회
export const getAnonymousProfile = () =>
  apiUnwrap(api.get<ApiResponse<AnonymousUserProfileResponse>>('users/anonymous/profile'))
