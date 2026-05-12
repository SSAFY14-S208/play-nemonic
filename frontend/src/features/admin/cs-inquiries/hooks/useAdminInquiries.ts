'use client'

import { useEffect, useState, useTransition } from 'react'
import { toast } from 'sonner'

import {
  ApiError,
  getAdminInquiryList,
  patchAdminInquiryStatus,
  postAdminInquiryReply,
} from '@/shared/apis'
import type {
  AdminInquiryListItem,
  AdminInquiryListParams,
  AdminInquiryReplyRequest,
  AdminInquiryReplyResponse,
  AdminInquiryStatus,
  AdminInquiryStatusUpdateResponse,
} from '@/shared/types'

// 백오피스 CS 문의 목록 + 필터 + 페이지네이션 + 상태 변경/답변.
//
// 필터 모델: 'ALL' | new | in_progress | resolved | closed

export type InquiryStatusFilter = AdminInquiryStatus | 'ALL'

/** PATCH/POST 성공 후 호출 — 페이지가 detail/list 양쪽을 동기화하는 데 사용. */
export type InquiryStatusMutationSuccess = (
  updated: AdminInquiryStatusUpdateResponse,
) => void
export type InquiryReplyMutationSuccess = (
  updated: AdminInquiryReplyResponse,
) => void

const PAGE_SIZE = 20

function buildListParams(
  filter: InquiryStatusFilter,
  keyword: string,
  page: number,
): AdminInquiryListParams {
  const params: AdminInquiryListParams = { page, size: PAGE_SIZE }
  if (filter !== 'ALL') params.status = filter
  if (keyword) params.keyword = keyword
  return params
}

export function useAdminInquiries() {
  const [items, setItems] = useState<AdminInquiryListItem[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [page, setPage] = useState(0)
  const [filter, setFilter] = useState<InquiryStatusFilter>('ALL')
  const [inputKeyword, setInputKeyword] = useState('')
  const [committedKeyword, setCommittedKeyword] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [isMutating, startMutationTransition] = useTransition()

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const response = await getAdminInquiryList(
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
            : '문의 목록을 불러오지 못했어요'
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

  const changeFilter = (next: InquiryStatusFilter) => {
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

  const changeStatus = (
    inquiryId: number,
    nextStatus: AdminInquiryStatus,
    onSuccess?: InquiryStatusMutationSuccess,
  ) => {
    if (isMutating) return
    startMutationTransition(async () => {
      try {
        const response = await patchAdminInquiryStatus(inquiryId, {
          status: nextStatus,
        })
        // 응답이 부분 갱신값(id/status/updatedAt)이라 list item에 머지.
        setItems((prev) =>
          prev.map((item) =>
            item.id === response.id
              ? { ...item, status: response.status, updatedAt: response.updatedAt }
              : item,
          ),
        )
        toast.success('상태를 변경했어요')
        onSuccess?.(response)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '상태 변경에 실패했어요'
        toast.error(message)
      }
    })
  }

  const reply = (
    inquiryId: number,
    payload: AdminInquiryReplyRequest,
    onSuccess?: InquiryReplyMutationSuccess,
  ) => {
    if (isMutating) return
    if (!payload.subject.trim() || !payload.message.trim()) {
      toast.error('제목과 본문을 입력하세요')
      return
    }
    startMutationTransition(async () => {
      try {
        const response = await postAdminInquiryReply(inquiryId, payload)
        // 응답은 id/status/respondedAt만 — list item에는 status/updatedAt만 갱신.
        setItems((prev) =>
          prev.map((item) =>
            item.id === response.id
              ? {
                  ...item,
                  status: response.status,
                  updatedAt: response.respondedAt,
                }
              : item,
          ),
        )
        toast.success('답변을 발송했어요')
        onSuccess?.(response)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '답변 발송에 실패했어요'
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
    changeStatus,
    reply,
  }
}
