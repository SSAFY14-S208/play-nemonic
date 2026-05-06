import ky from 'ky'

import { runtime } from '@/shared/config'
import { useAdminAuthStore } from '@/shared/stores'
import type { ApiResponse, LoginResponse } from '@/shared/types'

/**
 * 백오피스 관리자 전용 ky 인스턴스.
 *
 * 일반 apiClient와 분리한 이유:
 *
 * 1) 인증 모델이 다르다 — 일반 client는 익명 UUID(body/query 필드)로 식별하고
 *    Authorization 헤더를 모른다. 관리자 백오피스는 Bearer access token을 필요로
 *    하며 만료 시 reissue 흐름이 필요하다. 두 모델을 한 client에 섞으면 트랜스포트
 *    레이어가 인증 모델을 알게 되어 CLAUDE.md의 "apiClient는 인증 모델 모름" 원칙이
 *    무너진다.
 * 2) 401 → reissue → retry 인터셉터를 한 곳에 둘 수 있다 — 모든 admin 도메인
 *    함수가 토큰 갱신 흐름을 신경쓰지 않아도 된다.
 * 3) 도메인 함수 시그니처가 깔끔해진다 — `getAdminList()`처럼 토큰 인자 없이
 *    호출 가능. 토큰은 store에서 자동 주입.
 *
 * 토큰 보관 위치는 `useAdminAuthStore`(sessionStorage). 일반 사용자 store와 별도로
 * 관리되며, 두 인증 흐름은 서로 read하지 않는다.
 */

type Query = Record<string, string | number | boolean>

let pendingReissue: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  if (pendingReissue) return pendingReissue

  pendingReissue = (async () => {
    try {
      const refreshToken = useAdminAuthStore.getState().refreshToken
      if (!refreshToken) return null

      const envelope = await ky
        .post(`${runtime.apiUrl}/api/v1/auth/reissue`, {
          json: { refreshToken },
          timeout: 30_000,
        })
        .json<ApiResponse<LoginResponse>>()

      if (!envelope.success || !envelope.data) {
        useAdminAuthStore.getState().clear()
        return null
      }

      useAdminAuthStore.getState().setTokens(envelope.data)
      return envelope.data.accessToken
    } catch {
      useAdminAuthStore.getState().clear()
      return null
    } finally {
      pendingReissue = null
    }
  })()

  return pendingReissue
}

const adminClient = ky.create({
  prefix: `${runtime.apiUrl}/api/v1`,
  timeout: 30_000,
  hooks: {
    beforeRequest: [
      ({ request }) => {
        const accessToken = useAdminAuthStore.getState().accessToken
        if (accessToken) {
          request.headers.set('Authorization', `Bearer ${accessToken}`)
        }
      },
    ],
    afterResponse: [
      async ({ request, response }) => {
        if (response.status !== 401) return
        if (request.url.includes('/auth/reissue')) return

        const newAccessToken = await refreshAccessToken()
        if (!newAccessToken) return

        const retryRequest = request.clone()
        retryRequest.headers.set('Authorization', `Bearer ${newAccessToken}`)
        return fetch(retryRequest)
      },
    ],
  },
})

export const adminApi = {
  get: <T>(path: string, searchParams?: Query) =>
    adminClient.get(path, searchParams ? { searchParams } : undefined).json<T>(),
  post: <T>(path: string, body?: unknown) =>
    adminClient.post(path, body !== undefined ? { json: body } : undefined).json<T>(),
  put: <T>(path: string, body?: unknown) =>
    adminClient.put(path, body !== undefined ? { json: body } : undefined).json<T>(),
  patch: <T>(path: string, body?: unknown) =>
    adminClient.patch(path, body !== undefined ? { json: body } : undefined).json<T>(),
  delete: <T>(path: string, searchParams?: Query) =>
    adminClient.delete(path, searchParams ? { searchParams } : undefined).json<T>(),
}
