'use client'

import { useEffect, useState, useTransition } from 'react'
import { HTTPError } from 'ky'
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
  ApiResponse,
} from '@/shared/types'

async function getAdminAccountErrorMessage(
  caughtError: unknown,
  fallbackMessage: string,
) {
  if (caughtError instanceof ApiError) return caughtError.message

  if (caughtError instanceof HTTPError) {
    try {
      const response = (await caughtError.response
        .clone()
        .json()) as Partial<ApiResponse<unknown>>
      const fieldMessages = response.errors
        ? Object.values(response.errors).filter(Boolean)
        : []

      if (fieldMessages.length > 0) return fieldMessages.join('\n')
      if (response.message?.trim()) return response.message
    } catch {
      // 응답 본문이 JSON이 아니면 아래 fallback을 사용한다.
    }

    if (caughtError.response.status === 403) {
      return '슈퍼 관리자 권한이 필요합니다.'
    }
  }

  return fallbackMessage
}

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
        const message = await getAdminAccountErrorMessage(
          caughtError,
          '관리자 목록을 불러오지 못했어요',
        )
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
        const message = await getAdminAccountErrorMessage(
          caughtError,
          '계정 생성에 실패했어요',
        )
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
        const message = await getAdminAccountErrorMessage(
          caughtError,
          '비밀번호 변경에 실패했어요',
        )
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
        const message = await getAdminAccountErrorMessage(
          caughtError,
          '계정 삭제에 실패했어요',
        )
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
