// CS 문의 도메인 (OpenAPI: tag "CS 문의")

export type InquiryType =
  | 'BUG_REPORT'
  | 'FEATURE_REQUEST'
  | 'GENERAL_INQUIRY'
  | 'ACCOUNT_ISSUE'
  | 'PAYMENT_ISSUE'

export type InquiryStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'

export interface InquiryCreateRequest {
  type: InquiryType
  title: string
  content: string
  email?: string | null
}

export interface InquiryCreateResponse {
  inquiryId: string
  type: InquiryType
  title: string
  status: InquiryStatus
  createdAt: string
}
