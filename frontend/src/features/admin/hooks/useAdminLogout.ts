'use client'

import { useTransition } from 'react'

import { postLogout } from '@/shared/apis'
import { useAdminAuthStore } from '@/shared/stores'

interface UseAdminLogoutReturn {
  isPending: boolean
  logout: () => void
}

export function useAdminLogout(): UseAdminLogoutReturn {
  const refreshToken = useAdminAuthStore((state) => state.refreshToken)
  const clear = useAdminAuthStore((state) => state.clear)
  const [isPending, startTransition] = useTransition()

  const logout = () => {
    if (isPending) return

    startTransition(async () => {
      // 백엔드 블랙리스트 등록 실패해도 클라이언트는 무조건 토큰을 비운다.
      // 네트워크 오류 한 번에 사용자가 로그아웃을 못하면 UX가 막힌다 —
      // 서버 측 정리는 best-effort, 클라이언트 측 clear가 권한 단절의 ground truth.
      try {
        if (refreshToken) {
          await postLogout(refreshToken)
        }
      } catch {
        // swallow — clear()는 finally에서 보장.
      } finally {
        clear()
      }
    })
  }

  return { isPending, logout }
}
