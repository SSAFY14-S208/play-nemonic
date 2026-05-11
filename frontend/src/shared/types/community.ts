// Community 도메인 (OpenAPI: tag "Community")

export type MemoSourceType = 'DIRECT' | 'GALLERY'
export type MemoVisibleStatus = 'VISIBLE' | 'HIDDEN'
export type MemoModerationStatus = 'PENDING' | 'ALLOWED' | 'BLOCKED'
export type MemoReportReason = '욕설/비방/혐오' | '스팸/광고' | '개인정보유출' | '기타'

export interface CommunityMemoResponse {
  memoId: string
  userUuid: string
  nickname: string
  sourceType: MemoSourceType
  sourceGalleryId: string | null
  originalFileId: string
  thumbnailFileId: string
  positionX: number
  positionY: number
  zIndex: number
  rotationDeg: number
  decoration: unknown
  clientText: string
  visibleStatus: MemoVisibleStatus
  hiddenReason: string | null
  moderationStatus: MemoModerationStatus
  createdAt: string
  updatedAt: string
}

export interface CommunityMemoListResponse {
  memos: CommunityMemoResponse[]
  totalCount: number
  pageNumber: number
  pageSize: number
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
  decoration: unknown
  clientText: string
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
  reportId: string
  memoId: string
  reason: MemoReportReason
  reasonDetail: string
  reportCount: number
  createdAt: string
}
