// Phone 도메인 (OpenAPI: tag "Phone Drawing")

export interface PhoneDrawingSaveRequest {
  imageFileId: string
  thumbnailFileId?: string | null
  meta?: Record<string, unknown> | null
}

export interface PhoneDrawingSaveResponse {
  galleryId: string
  artifactId: string
  kind: string
  thumbnailUrl: string
  contentUrl: string
  createdAt: string
}
