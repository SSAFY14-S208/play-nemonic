// Admins 도메인 (OpenAPI: tag "Admins")

export type AdminRole = 'super_admin' | 'admin' | 'viewer'
export type AssignableAdminRole = Exclude<AdminRole, 'super_admin'>

export interface AdminResponse {
  id: number
  loginId: string
  nickname: string
  email: string
  role: AdminRole
}

export interface AdminCreateRequest {
  loginId: string
  password: string
  nickname: string
  email: string
  role: AssignableAdminRole
}

export interface AdminPasswordChangeRequest {
  password: string
}
