// Admin Community 도메인 (OpenAPI: tag "Admin Community")
//
// Swagger(2026-05-12) 기준. user-facing `CommunityMemoResponse`와 필드 구성이
// 크게 달라 admin 전용 타입으로 분리한다.

import type { MemoSourceType } from './community'

/**
 * 관리자 응답의 moderationStatus는 **소문자** ('pending' | 'allowed' | 'blocked').
 * user-facing community의 대문자 enum과 다른 별도 케이스라 admin 전용으로 둔다.
 */
export type AdminMemoModerationStatus = 'pending' | 'allowed' | 'blocked'

/**
 * 신고 사유 enum 값은 백엔드가 영어 키워드로 내려준다 (예: "inappropriate").
 * 전체 값 목록을 아직 확정하지 못해 string으로 두고, 화면에서 분기 처리할 때 좁힌다.
 * user-facing `MemoReportReason`(한글 enum)과는 별도.
 */
export type AdminMemoReportReason = string

/**
 * 메모 목록 응답 item — `getAdminCommunityMemoList` 전용.
 * 상세 응답(`AdminCommunityMemoDetailResponse`)은 여기에 decoration·reports를 더한다.
 */
export interface AdminCommunityMemoResponse {
  memoId: string
  authorUserUuid: string
  authorNickname: string
  sourceType: MemoSourceType
  artifactId: string
  artifactKind: string
  memoImageUrl: string
  memoOriginalImageUrl: string
  memoThumbnailImageUrl: string
  positionX: number
  positionY: number
  zIndex: number
  rotationDeg: number
  reportCount: number
  isHidden: boolean
  hiddenReason: string | null
  hiddenAt: string | null
  moderationStatus: AdminMemoModerationStatus
  ocrText: string | null
  /**
   * OCR 분류 결과. 백엔드는 JSON 문자열·배열·null 등 들쭉날쭉한 형태로 내려주지만
   * `adminCommunityApi`의 응답 정규화 단계에서 항상 `string[]`로 변환된다.
   */
  ocrCategories: string[]
  /** 마지막 검토 관리자 ID (없으면 null). */
  reviewedBy: number | null
  reviewedAt: string | null
  /** 메모가 캔버스에 부착된 시각. */
  attachedAt: string
  createdAt: string
  updatedAt: string
}

export interface AdminCommunityMemoListResponse {
  items: AdminCommunityMemoResponse[]
  page: number
  size: number
  totalElements: number
  hasNext: boolean
}

/**
 * 관리자 메모 상세(`getAdminCommunityMemo`) 응답.
 * 목록 항목에 decoration 객체와 신고 내역 배열이 추가된다.
 */
export interface AdminCommunityMemoDetailResponse extends AdminCommunityMemoResponse {
  /** 자유 형식 JSON 객체. 스티커·텍스트 데코 설정 등을 담는다. */
  decoration: Record<string, unknown>
  /** 메모에 누적된 신고 내역 전체. */
  reports: AdminCommunityMemoReportResponse[]
}

/**
 * 관리자 메모 신고 항목.
 * Swagger 기준 — 상세 응답의 nested 모양에 맞춰 정의. standalone `/reports` 엔드포인트도
 * 같은 DTO를 쓸 것으로 보이지만 미검증.
 */
export interface AdminCommunityMemoReportResponse {
  reportId: number
  memoId: string
  reporterUserUuid: string
  reporterNickname: string
  reason: AdminMemoReportReason
  reasonDetail: string | null
  createdAt: string
}

/**
 * `GET /admin/community/memos/{memoId}/reports` 응답.
 * Swagger(2026-05-12) 기준 — items 배열 + page/size/totalElements/hasNext wrapper.
 */
export interface AdminCommunityMemoReportListResponse {
  items: AdminCommunityMemoReportResponse[]
  page: number
  size: number
  totalElements: number
  hasNext: boolean
}

export interface AdminCommunityMemoReviewRequest {
  reason: string
}

export interface AdminCommunityMemoListParams {
  hidden?: boolean
  moderationStatus?: AdminMemoModerationStatus
  sourceType?: MemoSourceType
  reported?: boolean
  keyword?: string
  page?: number
  size?: number
}

export interface AdminCommunityMemoReportListParams {
  reason?: AdminMemoReportReason
  page?: number
  size?: number
}
