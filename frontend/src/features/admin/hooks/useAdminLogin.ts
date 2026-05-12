'use client'

import { useState, useTransition } from 'react'

import { ApiError, postLogin } from '@/shared/apis'
import { useAdminAuthStore } from '@/shared/stores'

interface UseAdminLoginReturn {
  isPending: boolean
  // 백엔드가 errors 맵으로 필드별 검증 메시지를 내려주면 그쪽을 우선 표시하고,
  // 아니면 일반 message는 generalError로 떨어진다 (useRelayNickname과 동일 패턴).
  fieldErrors: { loginId: string | null; password: string | null }
  generalError: string | null
  submit: (loginId: string, password: string) => void
  clearError: () => void
}

const EMPTY_FIELD_ERRORS = { loginId: null, password: null }

export function useAdminLogin(): UseAdminLoginReturn {
  const setTokens = useAdminAuthStore((state) => state.setTokens)

  const [isPending, startTransition] = useTransition()
  const [fieldErrors, setFieldErrors] = useState<UseAdminLoginReturn['fieldErrors']>(
    EMPTY_FIELD_ERRORS,
  )
  const [generalError, setGeneralError] = useState<string | null>(null)

  const submit = (rawLoginId: string, rawPassword: string) => {
    if (isPending) return

    const loginId = rawLoginId.trim()
    const password = rawPassword

    if (!loginId || !password) {
      setFieldErrors({
        loginId: !loginId ? '아이디를 입력해주세요' : null,
        password: !password ? '비밀번호를 입력해주세요' : null,
      })
      setGeneralError(null)
      return
    }

    setFieldErrors(EMPTY_FIELD_ERRORS)
    setGeneralError(null)

    startTransition(async () => {
      try {
        const tokens = await postLogin({ loginId, password })
        setTokens(tokens)
      } catch (caughtError) {
        if (caughtError instanceof ApiError) {
          // ApiError.errors 맵에 필드별 검증 메시지가 들어오면 그걸 인풋 아래에 띄운다.
          // shared/apis/apiError.ts 주석 참고.
          if (caughtError.errors?.loginId || caughtError.errors?.password) {
            setFieldErrors({
              loginId: caughtError.errors.loginId ?? null,
              password: caughtError.errors.password ?? null,
            })
            return
          }
          setGeneralError(caughtError.message)
          return
        }
        setGeneralError('로그인에 실패했어요')
      }
    })
  }

  const clearError = () => {
    setFieldErrors(EMPTY_FIELD_ERRORS)
    setGeneralError(null)
  }

  return { isPending, fieldErrors, generalError, submit, clearError }
}
