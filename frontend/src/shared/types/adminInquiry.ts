// Admin Inquiry 도메인 (OpenAPI: tag "관리자 문의")
//
// Swagger(2026-05-12)로 list/detail 응답을 검증. user-facing `InquiryStatus`/`InquiryType`
// (대문자)와 별도로 admin 응답은 소문자라 별도 타입을 둔다.

/**
 * 백엔드 enum: new / in_progress / resolved / closed (소문자).
 * user-facing `InquiryStatus`(대문자)와 격리.
 */
export type AdminInquiryStatus = 'new' | 'in_progress' | 'resolved' | 'closed'

/**
 * 문의 유형 enum 값. Swagger 예시는 `"error"` 한 종류만 노출 — 전체 값 미확정이라 string.
 */
export type AdminInquiryType = string

/**
 * `GET /admin/inquiries` 응답 항목.
 * detail 응답(`AdminInquiryDetailResponse`)은 여기에 content/attachments/meta 등을 더한다.
 */
export interface AdminInquiryListItem {
  /** int64. path parameter에는 string으로 전달 가능. */
  id: number
  /** 익명 사용자 UUID. 응답 필드명은 `userId`지만 query parameter는 `userUuid`임에 주의. */
  userId: string
  type: AdminInquiryType
  title: string
  email: string
  status: AdminInquiryStatus
  createdAt: string
  updatedAt: string
}

export interface AdminInquiryListResponse {
  items: AdminInquiryListItem[]
  page: number
  size: number
  totalElements: number
  hasNext: boolean
}

/**
 * `GET /admin/inquiries/{inquiryId}` 응답.
 * 목록 항목에 운영자 후속 처리 필드(`assignedTo`, `responseNote`, `respondedAt`)와
 * 사용자 첨부물·환경 메타가 추가된다.
 */
export interface AdminInquiryDetailResponse extends AdminInquiryListItem {
  content: string
  /** 첨부 파일 URL 목록. */
  attachments: string[]
  /** 문의 작성 환경 메타데이터 (브라우저·OS·UA 등). free-form. */
  meta: Record<string, unknown>
  /** 담당 관리자 ID (미할당이면 null로 가정 — Swagger는 nullable 명시 안 함). */
  assignedTo: number | null
  /** 관리자 내부 메모 또는 답변 내용 (미응답이면 null). */
  responseNote: string | null
  /** 답변 처리 시각 (미응답이면 null). */
  respondedAt: string | null
}

export interface AdminInquiryListParams {
  status?: AdminInquiryStatus
  type?: AdminInquiryType
  keyword?: string
  /**
   * 쿼리 파라미터명은 `userUuid` — 응답의 `userId` 필드와 이름이 다르다.
   * 백엔드 컨벤션 불일치라 우리 시그니처에서도 이대로 둔다.
   */
  userUuid?: string
  page?: number
  size?: number
}

/**
 * POST `/admin/inquiries/{inquiryId}/reply` — 운영자가 이메일로 회신.
 * body는 `{ subject, message }` 형태이며 응답은 부분 갱신값만 돌려준다.
 */
export interface AdminInquiryReplyRequest {
  subject: string
  message: string
}

export interface AdminInquiryReplyResponse {
  id: number
  status: AdminInquiryStatus
  respondedAt: string
}

/**
 * PATCH `/admin/inquiries/{inquiryId}/status` — 처리 상태 변경.
 * Swagger의 request body schema는 `status: string`이라 호출 측에서 enum 값으로 좁힌다.
 * 응답은 변경된 status + updatedAt만 부분 갱신값으로 돌려준다.
 */
export interface AdminInquiryStatusRequest {
  status: AdminInquiryStatus
}

export interface AdminInquiryStatusUpdateResponse {
  id: number
  status: AdminInquiryStatus
  updatedAt: string
}
