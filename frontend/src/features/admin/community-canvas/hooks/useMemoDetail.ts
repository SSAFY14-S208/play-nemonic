'use client'

import { useEffect, useState } from 'react'

import { ApiError, getAdminCommunityMemo } from '@/shared/apis'
import type { AdminCommunityMemoDetailResponse } from '@/shared/types'

// memoId가 변경될 때마다 상세를 페치한다. memoId가 null이면 상태를 리셋한다.
// 호출 측이 PATCH 응답을 받아 동기화하고 싶을 때를 위해 `setDetail`도 노출.

export interface UseMemoDetailReturn {
  detail: AdminCommunityMemoDetailResponse | null
  isLoading: boolean
  error: string | null
  setDetail: (next: AdminCommunityMemoDetailResponse | null) => void
}

export function useMemoDetail(memoId: string | null): UseMemoDetailReturn {
  const [detail, setDetail] = useState<AdminCommunityMemoDetailResponse | null>(
    null,
  )
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (!memoId) {
        setDetail(null)
        setError(null)
        setIsLoading(false)
        return
      }
      setIsLoading(true)
      try {
        const response = await getAdminCommunityMemo(memoId)
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
  }, [memoId])

  return { detail, isLoading, error, setDetail }
}
