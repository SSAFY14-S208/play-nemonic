// User 도메인 (OpenAPI: tag "User")

export interface AnonymousUserResponse {
  userUuid: string
  nickname: string
  createdAt: string
}

export interface AnonymousUserVerifyResponse {
  userUuid: string
  nickname: string
  lastSeenAt: string
}

export interface AnonymousUserBirthInfoRequest {
  birthday: string
  birthtime: string
  isLunar: boolean
}

export interface AnonymousUserBirthInfoResponse {
  userUuid: string
  birthday: string
  birthtime: string
  isLunar: boolean
  updatedAt: string
}

export interface AnonymousUserNicknameRequest {
  nickname: string
}

export interface AnonymousUserNicknameResponse {
  userUuid: string
  nickname: string
  updatedAt: string
}

export interface AnonymousUserProfileResponse {
  userUuid: string
  nickname: string
  birthday: string | null
  birthtime: string | null
  isLunar: boolean | null
  createdAt: string
  updatedAt: string
  lastSeenAt: string
}
