import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'

import type { AdminResponse } from '@/shared/types'

interface AdminAuthTokens {
  accessToken: string
  refreshToken: string
  expiresAt: string
  refreshTokenExpiresAt: string
  admin: AdminResponse
}

interface AdminAuthState {
  accessToken: string | null
  refreshToken: string | null
  expiresAt: string | null
  refreshTokenExpiresAt: string | null
  admin: AdminResponse | null
  setTokens: (tokens: AdminAuthTokens) => void
  clear: () => void
}

// sessionStorage를 쓰는 이유:
//   백오피스 access/refresh 토큰은 익명 userUuid보다 민감하다. 탭을 닫으면 사라지도록
//   sessionStorage에 보관해, 디바이스 공유 환경에서 다음 사용자가 그대로 로그인된 채로
//   접근하는 위험을 줄인다. 익명 UUID는 재발급 가능하지만 관리자 토큰은 재발급되면
//   바로 백엔드 권한이 따라붙는다.
export const useAdminAuthStore = create<AdminAuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      expiresAt: null,
      refreshTokenExpiresAt: null,
      admin: null,
      setTokens: ({ accessToken, refreshToken, expiresAt, refreshTokenExpiresAt, admin }) =>
        set({ accessToken, refreshToken, expiresAt, refreshTokenExpiresAt, admin }),
      clear: () =>
        set({
          accessToken: null,
          refreshToken: null,
          expiresAt: null,
          refreshTokenExpiresAt: null,
          admin: null,
        }),
    }),
    {
      name: 'nemonic-admin-auth',
      storage: createJSONStorage(() => sessionStorage),
    },
  ),
)
