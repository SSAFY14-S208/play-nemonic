'use client'

import { useCallback, useState } from 'react'
import { ApiError, patchAnonymousNickname } from '@/shared/apis'
import { useUserStore } from '@/shared/stores'

interface UseCommunityNicknameArgs {
  onSuccess?: () => void
}

export function useCommunityNickname({ onSuccess }: UseCommunityNicknameArgs = {}) {
  const setUser = useUserStore((state) => state.setUser)
  const [isPending, setIsPending] = useState(false)
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [generalError, setGeneralError] = useState<string | null>(null)

  const clearError = useCallback(() => {
    setFieldError(null)
    setGeneralError(null)
  }, [])

  const submit = useCallback(
    (rawNickname: string) => {
      if (isPending) return

      const nickname = rawNickname.trim()
      if (!nickname) {
        setFieldError('닉네임을 입력해주세요')
        return
      }

      clearError()
      setIsPending(true)
      void (async () => {
        try {
          const updatedUser = await patchAnonymousNickname({ nickname })
          setUser(updatedUser.userUuid, updatedUser.nickname)
          onSuccess?.()
        } catch (caughtError) {
          if (caughtError instanceof ApiError) {
            if (caughtError.errors?.nickname) {
              setFieldError(caughtError.errors.nickname)
              return
            }

            setGeneralError(caughtError.message)
            return
          }

          setGeneralError('닉네임 변경에 실패했어요.')
        } finally {
          setIsPending(false)
        }
      })()
    },
    [clearError, isPending, onSuccess, setUser],
  )

  return {
    isPending,
    fieldError,
    generalError,
    submit,
    clearError,
  } as const
}
