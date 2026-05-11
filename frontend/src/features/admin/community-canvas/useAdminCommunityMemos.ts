'use client'

import { useEffect, useState, useTransition } from 'react'
import { toast } from 'sonner'

import {
  ApiError,
  getAdminCommunityMemoList,
  patchAdminCommunityMemoHide,
  patchAdminCommunityMemoRestore,
} from '@/shared/apis'
import type {
  AdminCommunityMemoListParams,
  AdminCommunityMemoResponse,
} from '@/shared/types'

// 백오피스 커뮤니티 메모 목록 + 필터 + 페이지네이션 + 숨김/복원.
//
// 필터 모델:
//   - 'ALL'      → 전체 (백엔드 필터 안 보냄)
//   - 'VISIBLE'  → hidden=false
//   - 'HIDDEN'   → hidden=true
//   - 'REPORTED' → reported=true (숨김 여부 무관)

export type MemoListFilter = 'ALL' | 'VISIBLE' | 'HIDDEN' | 'REPORTED'

const PAGE_SIZE = 20

function buildListParams(
  filter: MemoListFilter,
  keyword: string,
  page: number,
): AdminCommunityMemoListParams {
  const params: AdminCommunityMemoListParams = { page, size: PAGE_SIZE }
  if (filter === 'VISIBLE') params.hidden = false
  if (filter === 'HIDDEN') params.hidden = true
  if (filter === 'REPORTED') params.reported = true
  if (keyword) params.keyword = keyword
  return params
}

export function useAdminCommunityMemos() {
  const [items, setItems] = useState<AdminCommunityMemoResponse[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [page, setPage] = useState(0)
  const [filter, setFilter] = useState<MemoListFilter>('ALL')
  const [inputKeyword, setInputKeyword] = useState('')
  const [committedKeyword, setCommittedKeyword] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [isMutating, startMutationTransition] = useTransition()

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const response = await getAdminCommunityMemoList(
          buildListParams(filter, committedKeyword, page),
        )
        if (cancelled) return
        setItems(response.items)
        setTotalElements(response.totalElements)
        setLoadError(null)
      } catch (caughtError) {
        if (cancelled) return
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '메모 목록을 불러오지 못했어요'
        setLoadError(message)
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [filter, page, committedKeyword])

  const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE))

  const goToPage = (next: number) => {
    setPage(Math.max(0, Math.min(totalPages - 1, next)))
  }

  const changeFilter = (next: MemoListFilter) => {
    setFilter(next)
    setPage(0)
  }

  const commitKeyword = () => {
    setCommittedKeyword(inputKeyword.trim())
    setPage(0)
  }

  const clearKeyword = () => {
    setInputKeyword('')
    setCommittedKeyword('')
    setPage(0)
  }

  const hide = (memoId: string, reason: string, onSuccess?: () => void) => {
    if (isMutating || !reason.trim()) return
    startMutationTransition(async () => {
      try {
        const updated = await patchAdminCommunityMemoHide(memoId, { reason })
        // 상세 응답이지만 베이스 타입(AdminCommunityMemoResponse)으로 호환 — 추가 필드는 무시.
        setItems((prev) =>
          prev.map((memo) => (memo.memoId === memoId ? updated : memo)),
        )
        toast.success('메모를 숨김 처리했어요')
        onSuccess?.()
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '숨김 처리에 실패했어요'
        toast.error(message)
      }
    })
  }

  const restore = (memoId: string, reason: string, onSuccess?: () => void) => {
    if (isMutating || !reason.trim()) return
    startMutationTransition(async () => {
      try {
        const updated = await patchAdminCommunityMemoRestore(memoId, { reason })
        setItems((prev) =>
          prev.map((memo) => (memo.memoId === memoId ? updated : memo)),
        )
        toast.success('메모를 복원했어요')
        onSuccess?.()
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '복원에 실패했어요'
        toast.error(message)
      }
    })
  }

  return {
    items,
    totalElements,
    page,
    totalPages,
    filter,
    inputKeyword,
    committedKeyword,
    isLoading,
    loadError,
    isMutating,
    setInputKeyword,
    commitKeyword,
    clearKeyword,
    changeFilter,
    goToPage,
    hide,
    restore,
  }
}
