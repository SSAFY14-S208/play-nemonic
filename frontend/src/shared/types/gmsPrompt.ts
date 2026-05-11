// GMS Prompts 도메인 (OpenAPI: tag "GMS Prompts")

export type GmsFeatureType = 'FORTUNE' | 'RELAY' | 'FLIPBOOK'

export interface GmsPromptResponse {
  promptId: number
  promptCode: string
  promptName: string
  promptContent: string
  featureType: GmsFeatureType
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface GmsPromptListResponse {
  prompts: GmsPromptResponse[]
  totalCount: number
  pageNumber: number
  pageSize: number
}

export interface GmsPromptCreateRequest {
  promptCode: string
  promptName: string
  promptContent: string
  featureType: GmsFeatureType
  isActive?: boolean
}

export interface GmsPromptUpdateRequest {
  promptName?: string
  promptContent?: string
  featureType?: GmsFeatureType
  isActive?: boolean
}

export interface GmsPromptListParams {
  keyword?: string
  featureType?: GmsFeatureType
  page?: number
  size?: number
}
