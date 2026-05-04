import { api } from '@/shared/libs'
import type {
  AnonymousUserBirthInfoRequest,
  AnonymousUserBirthInfoResponse,
  AnonymousUserNicknameResponse,
  AnonymousUserProfileResponse,
  AnonymousUserResponse,
  AnonymousUserVerifyResponse,
  ApiResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /users/anonymous — 익명 사용자 UUID 발급
export const postAnonymous = () =>
  apiUnwrap(api.post<ApiResponse<AnonymousUserResponse>>('users/anonymous'))

// POST /users/anonymous/verify — 익명 사용자 UUID 확인
export const postAnonymousVerify = (userUuid: string) =>
  apiUnwrap(
    api.post<ApiResponse<AnonymousUserVerifyResponse>>('users/anonymous/verify', { userUuid }),
  )

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
export const patchAnonymousNickname = (userUuid: string, nickname: string) =>
  apiUnwrap(
    api.patch<ApiResponse<AnonymousUserNicknameResponse>>('users/anonymous/nickname', {
      userUuid,
      nickname,
    }),
  )

// GET /users/anonymous/profile — 익명 사용자 프로필 조회
export const getAnonymousProfile = (userUuid: string) =>
  apiUnwrap(
    api.get<ApiResponse<AnonymousUserProfileResponse>>('users/anonymous/profile', { userUuid }),
  )
