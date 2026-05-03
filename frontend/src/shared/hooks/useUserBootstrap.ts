import { useEffect, useState } from 'react'

import { postAnonymous, postAnonymousVerify } from '@/shared/apis'
import { useUserStore } from '@/shared/stores'

// 첫 진입 시 익명 UUID를 발급/검증해 store + localStorage에 보관한다.
// persist 미들웨어의 hydration이 끝난 뒤에 동작하도록 대기한 뒤 1회만 호출.
//
// SSR/prerender 가드: persist 미들웨어는 클라이언트 전용이라 서버 평가 시점에는
// useUserStore.persist 객체가 정상적으로 부착되어 있지 않을 수 있다. typeof window
// 체크와 옵셔널 체이닝으로 그런 경로에서는 안전하게 no-op하도록 한다.
export function useUserBootstrap() {
  const [hydrated, setHydrated] = useState(
    () => typeof window !== 'undefined' && useUserStore.persist?.hasHydrated() === true,
  )

  useEffect(() => {
    if (hydrated) return
    if (typeof window === 'undefined' || !useUserStore.persist) return
    return useUserStore.persist.onFinishHydration(() => setHydrated(true))
  }, [hydrated])

  useEffect(() => {
    if (!hydrated) return
    let cancelled = false

    void (async () => {
      const stored = useUserStore.getState().userUuid

      if (stored) {
        try {
          const verified = await postAnonymousVerify(stored)
          if (cancelled) return
          useUserStore.getState().setUser(verified.userUuid, verified.nickname)
          return
        } catch {
          // verify 실패 시 새 익명 UUID 발급으로 폴백
        }
      }

      try {
        const created = await postAnonymous()
        if (cancelled) return
        useUserStore.getState().setUser(created.userUuid, created.nickname)
      } catch {
        // 네트워크 실패는 다음 부트스트랩 시도에서 재시도
      }
    })()

    return () => {
      cancelled = true
    }
  }, [hydrated])
}
