// GMS Prompts 도메인 (OpenAPI: tag "GMS Prompts")
//
// 실제 GET 응답(2026-05-12 확인) 기준:
//   data.items[].{ id, name, content, featureType, createdBy, createdAt, updatedAt }
//   data.{ items, page, size, totalElements, hasNext }
//
// Swagger의 components.schemas가 truncated되어 POST/PATCH body는 추정.
// 변경 가능한 필드는 (name, content, featureType)으로 본다 — id/createdBy/timestamps는
// 서버가 관리. 실제 거부되면 schema 추가 확인 후 조정.

// 백엔드 enum (Swagger `matches fortune|sticker` 패턴):
//   - 'fortune' : 오늘의 운세 생성 프롬프트
//   - 'sticker' : 무한캔버스의 생성형 AI 이미지(스티커) 프롬프트
// 대문자로 비교하면 필터·select가 매치되지 않으므로 lowercase로 통일.
export type GmsFeatureType = 'fortune' | 'sticker'

export interface GmsPromptResponse {
  id: number
  name: string
  content: string
  featureType: GmsFeatureType
  createdBy: number | null
  createdAt: string
  updatedAt: string
}

export interface GmsPromptListResponse {
  items: GmsPromptResponse[]
  page: number
  size: number
  totalElements: number
  hasNext: boolean
}

export interface GmsPromptCreateRequest {
  name: string
  content: string
  featureType: GmsFeatureType
}

export interface GmsPromptUpdateRequest {
  name?: string
  content?: string
  featureType?: GmsFeatureType
}

export interface GmsPromptListParams {
  keyword?: string
  featureType?: GmsFeatureType
  page?: number
  size?: number
}
