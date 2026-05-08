'use client'

import { useState, useTransition } from 'react'

import { ApiError, patchAnonymousNickname } from '@/shared/apis'
import { useUserStore } from '@/shared/stores'

interface UseRelayNicknameArgs {
  // 닉네임 갱신이 끝나면 부스가 보류했던 액션(방 만들기/방 입장 모달 열기)을
  // 이어서 수행해야 하므로, 컴포넌트가 콜백을 주입한다.
  onSuccess?: () => void
}

interface UseRelayNicknameReturn {
  isPending: boolean
  // 백엔드가 errors 맵에 nickname 키로 필드 검증 메시지를 내려주면 그걸 우선으로
  // 표시하고, 없으면 일반 message를 fallback으로 사용한다.
  fieldError: string | null
  generalError: string | null
  submit: (nickname: string) => void
  clearError: () => void
}

export function useRelayNickname({
  onSuccess,
}: UseRelayNicknameArgs = {}): UseRelayNicknameReturn {
  const setUser = useUserStore((state) => state.setUser)

  const [isPending, startTransition] = useTransition()
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [generalError, setGeneralError] = useState<string | null>(null)

  const submit = (rawNickname: string) => {
    if (isPending) return
    const nickname = rawNickname.trim()
    if (!nickname) {
      setFieldError('닉네임을 입력해주세요')
      return
    }
    setFieldError(null)
    setGeneralError(null)
    startTransition(async () => {
      try {
        const updated = await patchAnonymousNickname({ nickname })
        setUser(updated.userUuid, updated.nickname)
        onSuccess?.()
      } catch (caughtError) {
        if (caughtError instanceof ApiError) {
          // ApiError.errors는 백엔드가 필드별 검증 결과를 담아 내려주는 맵이다.
          // shared/apis/apiError.ts 주석 참고.
          if (caughtError.errors?.nickname) {
            setFieldError(caughtError.errors.nickname)
            return
          }
          setGeneralError(caughtError.message)
          return
        }
        setGeneralError('닉네임 변경에 실패했어요')
      }
    })
  }

  const clearError = () => {
    setFieldError(null)
    setGeneralError(null)
  }

  return { isPending, fieldError, generalError, submit, clearError }
}
