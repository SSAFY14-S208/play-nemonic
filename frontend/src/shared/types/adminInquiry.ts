// Admin Inquiry 도메인 (OpenAPI: tag "관리자 문의")

import type { InquiryStatus, InquiryType } from './inquiry'

export interface AdminInquiryReply {
  replyId: string
  replyContent: string
  repliedAt: string
}

export interface AdminInquiryResponse {
  inquiryId: string
  userUuid: string
  type: InquiryType
  title: string
  content: string
  email: string | null
  status: InquiryStatus
  replies: AdminInquiryReply[]
  createdAt: string
  updatedAt: string
}

export interface AdminInquiryListResponse {
  inquiries: AdminInquiryResponse[]
  totalCount: number
  pageNumber: number
  pageSize: number
}

export interface AdminInquiryReplyRequest {
  replyContent: string
  sendEmail?: boolean
}

export interface AdminInquiryReplyResponse {
  inquiryId: string
  replyId: string
  replyContent: string
  repliedAt: string
}

export interface AdminInquiryStatusRequest {
  status: InquiryStatus
}

export interface AdminInquiryStatusResponse {
  inquiryId: string
  status: InquiryStatus
  updatedAt: string
}

export interface AdminInquiryListParams {
  status?: InquiryStatus
  type?: InquiryType
  keyword?: string
  userUuid?: string
  page?: number
  size?: number
}
