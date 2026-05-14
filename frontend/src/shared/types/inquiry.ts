// CS 문의 도메인 (OpenAPI: tag "CS 문의")

/**
 * 백엔드 CsInquiryType enum 매핑 (소문자).
 * ERROR("error"), FEATURE_REQUEST("feature_request"),
 * CONTENT_REPORT("content_report"), OTHER("other")
 */
export type CsInquiryType =
  | 'error'
  | 'feature_request'
  | 'content_report'
  | 'other'

export interface InquiryCreateRequest {
  type: CsInquiryType
  title: string
  content: string
  email?: string | null
}

export interface InquiryCreateResponse {
  id: number
  status: string
  createdAt: string
}
