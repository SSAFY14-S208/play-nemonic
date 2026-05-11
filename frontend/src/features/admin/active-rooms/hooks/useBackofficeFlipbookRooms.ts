'use client'

import { useEffect, useMemo, useState, useTransition } from 'react'
import { toast } from 'sonner'

import {
  ApiError,
  deleteBackofficeFlipbookRoom,
  getBackofficeFlipbookRoomList,
} from '@/shared/apis'
import type { BackofficeFlipbookRoomResponse } from '@/shared/types'

// 백오피스 활성 플립북 방 목록 + 필터 + 페이지네이션 + 강제 종료.

export type FlipbookRoomStatusFilter =
  | 'ALL'
  | 'WAITING'
  | 'PLAYING'
  | 'FINISHED'

const PAGE_SIZE = 20

export function useBackofficeFlipbookRooms() {
  const [items, setItems] = useState<BackofficeFlipbookRoomResponse[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<FlipbookRoomStatusFilter>('ALL')
  const [keyword, setKeyword] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [isMutating, startMutationTransition] = useTransition()

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const response = await getBackofficeFlipbookRoomList({
          status: statusFilter === 'ALL' ? undefined : statusFilter,
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
            : '방 목록을 불러오지 못했어요'
        setLoadError(message)
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [statusFilter, page])

  const visibleItems = useMemo(() => {
    const trimmed = keyword.trim().toLowerCase()
    if (!trimmed) return items
    return items.filter((item) => item.roomCode.toLowerCase().includes(trimmed))
  }, [items, keyword])

  const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE))

  const goToPage = (next: number) => {
    setPage(Math.max(0, Math.min(totalPages - 1, next)))
  }

  const changeStatusFilter = (next: FlipbookRoomStatusFilter) => {
    setStatusFilter(next)
    setPage(0)
  }

  const forceClose = (roomCode: string) => {
    if (isMutating) return
    if (
      typeof window !== 'undefined' &&
      !window.confirm(`방 ${roomCode}을(를) 강제 종료할까요?`)
    )
      return

    startMutationTransition(async () => {
      try {
        await deleteBackofficeFlipbookRoom(roomCode)
        setItems((prev) => prev.filter((room) => room.roomCode !== roomCode))
        setTotalElements((prev) => Math.max(0, prev - 1))
        toast.success(`방 ${roomCode}을(를) 종료했어요`)
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
    statusFilter,
    keyword,
    isLoading,
    loadError,
    isMutating,
    setKeyword,
    changeStatusFilter,
    goToPage,
    forceClose,
  }
}
