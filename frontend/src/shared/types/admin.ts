// Admins 도메인 (OpenAPI: tag "Admins")

export interface AdminResponse {
  id: number
  loginId: string
  nickname: string
  email: string
  role: string
}

export interface AdminCreateRequest {
  loginId: string
  password: string
  nickname: string
  email: string
}

export interface AdminPasswordChangeRequest {
  password: string
}
