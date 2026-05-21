// 모든 백엔드 응답을 감싸는 공통 봉투
export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  errors?: Record<string, string>
}
