// System Parameters 도메인 (OpenAPI: tag "System Parameters")

export interface SystemParameterResponse {
  parameterId: number
  parameterKey: string
  parameterValue: string
  description: string | null
  updatedAt: string
}

export interface SystemParameterListResponse {
  parameters: SystemParameterResponse[]
  totalCount: number
}

export interface SystemParameterBulkUpdateRequest {
  updates: Array<{
    parameterId: number
    parameterValue: string
  }>
}

export interface SystemParameterListParams {
  keyword?: string
}
