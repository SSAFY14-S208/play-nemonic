import { useEffect, useState } from 'react'

import { HTTPError } from 'ky'

import { ApiError, postAnonymous, postAnonymousVerify } from '@/shared/apis'
import { useUserStore } from '@/shared/stores'

// 첫 진입 시 익명 UUID를 발급/검증해 store + localStorage에 보관한다.
// persist 미들웨어의 hydration이 끝난 뒤에 동작하도록 대기한 뒤 1회만 호출.
//
// SSR/prerender 가드: persist 미들웨어는 클라이언트 전용이라 서버 평가 시점에는
// useUserStore.persist 객체가 정상적으로 부착되어 있지 않을 수 있다. typeof window
// 체크와 옵셔널 체이닝으로 그런 경로에서는 안전하게 no-op하도록 한다.

// verify 실패의 의미를 두 갈래로 나눈다:
//   1) 백엔드가 이 UUID를 더는 인정하지 않음 → 새 발급
//      - 200 + success:false 봉투(ApiError): 형식 오류 등 비즈니스 검증 실패
//      - 4xx HTTPError: 400(형식 오류) / 404(존재하지 않는 사용자)
//   2) 일시 장애 → 기존 UUID 유지, 다음 부트스트랩에서 재시도
//      - 5xx HTTPError: 서버 오류
//      - HTTPError가 아닌 throw: 네트워크 단절, TimeoutError 등
function isInvalidUuidError(error: unknown): boolean {
  if (error instanceof ApiError) return true
  if (error instanceof HTTPError) {
    return error.response.status >= 400 && error.response.status < 500
  }
  return false
}

export function useUserBootstrap() {
  const [hydrated, setHydrated] = useState(
    () => typeof window !== 'undefined' && useUserStore.persist?.hasHydrated() === true,
  )

  useEffect(() => {
    if (hydrated) return
    if (typeof window === 'undefined') return

    let cancelled = false
    const finishHydration = () => {
      if (!cancelled) {
        setHydrated(true)
      }
    }

    const fallbackTimerId = window.setTimeout(finishHydration, USER_BOOTSTRAP_HYDRATION_FALLBACK_DELAY_MS)

    if (!useUserStore.persist) {
      finishHydration()
      return () => {
        cancelled = true
        window.clearTimeout(fallbackTimerId)
      }
    }

    const unsubscribe = useUserStore.persist.onFinishHydration(finishHydration)

    ;(async () => {
      await Promise.resolve()

      if (!cancelled && useUserStore.persist?.hasHydrated() === true) {
        finishHydration()
      }
    })()

    return () => {
      cancelled = true
      window.clearTimeout(fallbackTimerId)
      unsubscribe()
    }
  }, [hydrated])

  useEffect(() => {
    if (!hydrated) return
    let cancelled = false

    void (async () => {
      const stored = useUserStore.getState().userUuid

      if (stored) {
        try {
          // userUuid는 apiClient의 beforeRequest 훅이 store에서 읽어
          // Anonymous-User-UUID 헤더로 자동 주입한다.
          const verified = await postAnonymousVerify()
          if (cancelled) return
          useUserStore.getState().setUser(verified.userUuid, verified.nickname)
          return
        } catch (error) {
          if (cancelled) return
          if (!isInvalidUuidError(error)) {
            // 일시 장애 — 기존 UUID 그대로 두고 종료. 다음 진입에서 재검증.
            return
          }
          // 백엔드가 이 UUID를 거부 — 폴백 발급 전에 stale 상태 정리.
          // store가 비어야 다음 postAnonymous 호출 시 헤더가 주입되지 않는다.
          useUserStore.getState().clear()
        }
      }

      try {
        const created = await postAnonymous()
        if (cancelled) return
        useUserStore.getState().setUser(created.userUuid, created.nickname)
      } catch {
        // 발급 실패도 일시 장애로 간주. 다음 부트스트랩 시도에서 재시도.
      }
    })()

    return () => {
      cancelled = true
    }
  }, [hydrated])
}

const USER_BOOTSTRAP_HYDRATION_FALLBACK_DELAY_MS = 500
