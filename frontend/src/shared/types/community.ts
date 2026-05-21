// Community 도메인 (OpenAPI: tag "Community")

export type MemoSourceType = 'DIRECT' | 'GALLERY'
export type MemoModerationStatus = 'pending' | 'allowed' | 'blocked'
export type MemoReportReason =
  | '부적절한 콘텐츠'
  | '욕설/비방/혐오'
  | '선정적/음란물'
  | '폭력적/위협적 표현'
  | '스팸/광고'
  | '개인정보 노출'
  | '도용/사칭'
  | '기타'
export type CommunityMemoReportReason = MemoReportReason

export interface CommunityMemoItemResponse {
  memoUuid: string
  authorNickname: string
  sourceType: MemoSourceType
  memoImageUrl: string
  memoOriginalImageUrl: string
  memoThumbnailImageUrl: string
  memoPlaybackImageUrl: string | null
  positionX: number
  positionY: number
  zIndex: number
  rotationDeg: number
  ownedByMe: boolean
  attachedAt: string
  decoration?: Record<string, unknown> | null
}

export interface CommunityMemoDetailResponse extends CommunityMemoItemResponse {
  decoration: Record<string, unknown>
  artifactId: string | null
  galleryContentKind: string | null
  moderationStatus: MemoModerationStatus | string
  reportCount: number
  createdAt: string
  updatedAt: string
}

export interface CommunityMemoListResponse {
  items: CommunityMemoItemResponse[]
  totalElements: number
}

export interface CommunityMemoCreateRequest {
  sourceType: MemoSourceType
  originalFileId: string
  thumbnailFileId: string
  sourceGalleryId?: string | null
  positionX: number
  positionY: number
  zIndex: number
  rotationDeg: number
  decoration?: unknown
  clientText?: string
}

export interface CommunityMemoLayoutRequest {
  positionX: number
  positionY: number
  zIndex: number
  rotationDeg: number
}

export interface CommunityMemoReportRequest {
  reason: MemoReportReason
  reasonDetail: string
}

export interface CommunityMemoReportResponse {
  memoId: string
  reportCount: number
  hidden: boolean
}
