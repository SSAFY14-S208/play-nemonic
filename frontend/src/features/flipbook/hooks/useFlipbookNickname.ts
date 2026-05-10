'use client'

import { useCallback, useState } from 'react'

import { ApiError, patchAnonymousNickname } from '@/shared/apis'
import { useUserStore } from '@/shared/stores'

interface UseFlipbookNicknameArgs {
  onSuccess?: () => void
}

interface UseFlipbookNicknameReturn {
  isPending: boolean
  fieldError: string | null
  generalError: string | null
  submit: (nickname: string) => void
  clearError: () => void
}

export function useFlipbookNickname({
  onSuccess,
}: UseFlipbookNicknameArgs = {}): UseFlipbookNicknameReturn {
  const setUser = useUserStore((state) => state.setUser)
  const [isPending, setIsPending] = useState(false)
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [generalError, setGeneralError] = useState<string | null>(null)

  const submit = useCallback((rawNickname: string) => {
    if (isPending) return
    const nickname = rawNickname.trim()

    if (!nickname) {
      setFieldError('닉네임을 입력해주세요.')
      return
    }

    setFieldError(null)
    setGeneralError(null)
    setIsPending(true)

    void (async () => {
      try {
        const updatedUser = await patchAnonymousNickname({ nickname })
        setUser(updatedUser.userUuid, updatedUser.nickname)
        onSuccess?.()
      } catch (error) {
        if (error instanceof ApiError) {
          if (error.errors?.nickname) {
            setFieldError(error.errors.nickname)
            return
          }

          setGeneralError(error.message)
          return
        }

        setGeneralError('닉네임 저장에 실패했어요.')
      } finally {
        setIsPending(false)
      }
    })()
  }, [isPending, onSuccess, setUser])

  const clearError = useCallback(() => {
    setFieldError(null)
    setGeneralError(null)
  }, [])

  return { isPending, fieldError, generalError, submit, clearError }
}
