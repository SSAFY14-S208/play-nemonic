'use client'

import { useEffect, useState, useTransition } from 'react'
import { toast } from 'sonner'

import {
  ApiError,
  deleteAdmin,
  getAdminList,
  patchAdminPassword,
  postAdmin,
} from '@/shared/apis'
import type {
  AdminCreateRequest,
  AdminPasswordChangeRequest,
  AdminResponse,
} from '@/shared/types'

export function useAdminAccounts() {
  const [items, setItems] = useState<AdminResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [isMutating, startMutationTransition] = useTransition()

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const response = await getAdminList()
        if (cancelled) return
        setItems(response)
        setLoadError(null)
      } catch (caughtError) {
        if (cancelled) return
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '관리자 목록을 불러오지 못했어요'
        setLoadError(message)
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const createAdmin = (
    payload: AdminCreateRequest,
    onSuccess?: () => void,
  ) => {
    if (isMutating) return
    startMutationTransition(async () => {
      try {
        const created = await postAdmin(payload)
        setItems((prev) => [...prev, created])
        toast.success('관리자 계정을 생성했어요')
        onSuccess?.()
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '계정 생성에 실패했어요'
        toast.error(message)
      }
    })
  }

  const changePassword = (
    adminId: number,
    payload: AdminPasswordChangeRequest,
    onSuccess?: () => void,
  ) => {
    if (isMutating) return
    startMutationTransition(async () => {
      try {
        await patchAdminPassword(adminId, payload)
        toast.success('비밀번호를 변경했어요')
        onSuccess?.()
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '비밀번호 변경에 실패했어요'
        toast.error(message)
      }
    })
  }

  const removeAdmin = (adminId: number) => {
    if (isMutating) return
    startMutationTransition(async () => {
      try {
        await deleteAdmin(adminId)
        setItems((prev) => prev.filter((item) => item.id !== adminId))
        toast.success('관리자 계정을 삭제했어요')
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '계정 삭제에 실패했어요'
        toast.error(message)
      }
    })
  }

  return {
    items,
    isLoading,
    loadError,
    isMutating,
    createAdmin,
    changePassword,
    removeAdmin,
  }
}
