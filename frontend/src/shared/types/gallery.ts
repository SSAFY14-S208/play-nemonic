// Gallery 도메인 (OpenAPI: tag "Gallery")

export interface GalleryItemResponse {
  galleryId: string
  artifactId: string
  kind: string
  thumbnailUrl: string
  contentUrl: string
  sourceRoomId: string | null
  createdAt: string
}

export interface GalleryListResponse {
  items: GalleryItemResponse[]
  page: number
  size: number
  totalElements: number
  hasNext: boolean
}

export interface GalleryDetailResponse {
  galleryId: string
  artifactId: string
  kind: string
  thumbnailUrl: string
  contentUrl: string
  sourceRoomId: string | null
  meta: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface GalleryDeleteResponse {
  galleryId: string
  artifactId: string
  deletedAt: string
}
