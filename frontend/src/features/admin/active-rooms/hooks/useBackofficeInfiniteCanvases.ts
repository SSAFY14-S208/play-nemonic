'use client'

import { useEffect, useMemo, useState, useTransition } from 'react'
import { toast } from 'sonner'

import {
  ApiError,
  deleteBackofficeInfiniteCanvas,
  getBackofficeInfiniteCanvasList,
} from '@/shared/apis'
import type { BackofficeInfiniteCanvasResponse } from '@/shared/types'

// 백오피스 활성 무한 캔버스 목록 + 검색 + 페이지네이션 + 강제 종료.

const PAGE_SIZE = 20

export function useBackofficeInfiniteCanvases() {
  const [items, setItems] = useState<BackofficeInfiniteCanvasResponse[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [page, setPage] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [isMutating, startMutationTransition] = useTransition()

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setIsLoading(true)
      try {
        const response = await getBackofficeInfiniteCanvasList({
          page,
          size: PAGE_SIZE,
        })
        if (cancelled) return
        setItems(response.items)
        setTotalElements(response.totalElements)
        setLoadError(null)
      } catch (caughtError) {
        if (cancelled) return
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '캔버스 목록을 불러오지 못했어요'
        setLoadError(message)
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [page])

  const visibleItems = useMemo(() => {
    const trimmed = keyword.trim().toLowerCase()
    if (!trimmed) return items
    return items.filter((item) => item.roomCode.toLowerCase().includes(trimmed))
  }, [items, keyword])

  const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE))

  const goToPage = (next: number) => {
    setPage(Math.max(0, Math.min(totalPages - 1, next)))
  }

  const forceClose = (roomCode: string) => {
    if (isMutating) return
    if (
      typeof window !== 'undefined' &&
      !window.confirm(`캔버스 ${roomCode}을(를) 강제 종료할까요?`)
    )
      return

    startMutationTransition(async () => {
      try {
        await deleteBackofficeInfiniteCanvas(roomCode)
        setItems((prev) => prev.filter((canvas) => canvas.roomCode !== roomCode))
        setTotalElements((prev) => Math.max(0, prev - 1))
        toast.success(`캔버스 ${roomCode}을(를) 종료했어요`)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '강제 종료에 실패했어요'
        toast.error(message)
      }
    })
  }

  return {
    items: visibleItems,
    isFiltered: keyword.trim().length > 0,
    totalElements,
    page,
    totalPages,
    keyword,
    isLoading,
    loadError,
    isMutating,
    setKeyword,
    goToPage,
    forceClose,
  }
}
