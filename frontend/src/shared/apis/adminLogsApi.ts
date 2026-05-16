import { adminApi } from '@/shared/libs'
import type {
  AdminLogsFieldSummaryRequest,
  AdminLogsFieldSummaryResponse,
  AdminLogsHistogramRequest,
  AdminLogsHistogramResponse,
  AdminLogsSearchRequest,
  AdminLogsSearchResponse,
} from '@/shared/types'

// 백오피스 로그 API — ApiResponse 봉투 없이 평면 응답.
// adminApi 인터셉터가 Authorization 헤더를 자동 주입한다.

// POST /admin/logs/search — 로그 검색
export const postAdminLogsSearch = (payload: AdminLogsSearchRequest) =>
  adminApi.post<AdminLogsSearchResponse>('admin/logs/search', payload)

// POST /admin/logs/histogram — 시계열 버킷
export const postAdminLogsHistogram = (payload: AdminLogsHistogramRequest) =>
  adminApi.post<AdminLogsHistogramResponse>('admin/logs/histogram', payload)

// POST /admin/logs/field-summary — 필드 top 값
export const postAdminLogsFieldSummary = (payload: AdminLogsFieldSummaryRequest) =>
  adminApi.post<AdminLogsFieldSummaryResponse>('admin/logs/field-summary', payload)
