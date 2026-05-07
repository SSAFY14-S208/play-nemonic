// Auth 도메인 (OpenAPI: tag "Auth")

import type { AdminResponse } from './admin'

export interface LoginRequest {
  loginId: string
  password: string
}

export interface LogoutRequest {
  refreshToken: string
}

export interface TokenRefreshRequest {
  refreshToken: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
  refreshToken: string
  refreshTokenExpiresAt: string
  admin: AdminResponse
}
