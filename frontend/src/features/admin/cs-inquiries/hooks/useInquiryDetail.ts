'use client'

import { useEffect, useState } from 'react'

import { ApiError, getAdminInquiry } from '@/shared/apis'
import type { AdminInquiryDetailResponse } from '@/shared/types'

// inquiryId가 변경될 때마다 상세를 페치한다. null이면 상태 리셋.
// 호출 측이 status/reply 부분 응답을 받아 머지하고 싶을 때를 위해 `setDetail` 노출.

export interface UseInquiryDetailReturn {
  detail: AdminInquiryDetailResponse | null
  isLoading: boolean
  error: string | null
  setDetail: (
    next:
      | AdminInquiryDetailResponse
      | null
      | ((
          prev: AdminInquiryDetailResponse | null,
        ) => AdminInquiryDetailResponse | null),
  ) => void
}

export function useInquiryDetail(
  inquiryId: number | string | null,
): UseInquiryDetailReturn {
  const [detail, setDetail] = useState<AdminInquiryDetailResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (inquiryId === null) {
        setDetail(null)
        setError(null)
        setIsLoading(false)
        return
      }
      setIsLoading(true)
      try {
        const response = await getAdminInquiry(inquiryId)
        if (cancelled) return
        setDetail(response)
        setError(null)
      } catch (caughtError) {
        if (cancelled) return
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '상세를 불러오지 못했어요'
        setError(message)
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [inquiryId])

  return { detail, isLoading, error, setDetail }
}
