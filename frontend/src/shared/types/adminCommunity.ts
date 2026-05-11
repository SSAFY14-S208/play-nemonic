// Admin Community 도메인 (OpenAPI: tag "Admin Community")

import type { MemoModerationStatus, MemoReportReason, MemoSourceType, MemoVisibleStatus } from './community'

export interface AdminCommunityMemoResponse {
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
  reportCount: number
  hiddenBy: string | null
  hiddenAt: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminCommunityMemoListResponse {
  memos: AdminCommunityMemoResponse[]
  totalCount: number
  pageNumber: number
  pageSize: number
}

export interface AdminCommunityMemoReportResponse {
  reportId: string
  userUuid: string
  reason: MemoReportReason
  reasonDetail: string
  createdAt: string
}

export interface AdminCommunityMemoReportListResponse {
  reports: AdminCommunityMemoReportResponse[]
  totalCount: number
  pageNumber: number
  pageSize: number
}

export interface AdminCommunityMemoReviewRequest {
  reason: string
}

export interface AdminCommunityMemoListParams {
  hidden?: boolean
  moderationStatus?: MemoModerationStatus
  sourceType?: MemoSourceType
  reported?: boolean
  keyword?: string
  page?: number
  size?: number
}

export interface AdminCommunityMemoReportListParams {
  reason?: MemoReportReason
  page?: number
  size?: number
}
