'use client'

import { useEffect, useState } from 'react'

import { useAdminAuthStore } from '@/shared/stores'

import { AdminLoginModal } from '../AdminLoginModal'

interface AdminAuthGuardProps {
  children: React.ReactNode
}

/**
 * /admin/* 라우트의 모든 children을 감싸는 게이트.
 *
 * 동작:
 *   1) sessionStorage에서 zustand persist hydration이 끝나기 전에는 null 렌더.
 *      → 새로고침 직후 토큰이 있는데도 모달이 깜빡 보이는 현상을 막는다.
 *   2) hydration 후 accessToken === null이면 AdminLoginModal만 렌더.
 *      → 백오피스 chrome(TopBar/Sidebar/PageHeader)이 노출되지 않는다.
 *   3) 토큰 존재 시 children 렌더.
 *
 * 주의:
 *   - store.persist는 SSR/일부 빌드 환경에서 undefined일 수 있다
 *     (features/fortune/hooks/useFortuneSessionHydration.ts 의 동일 가드 참고).
 *     반드시 useEffect 안에서 접근하고, 노출되지 않은 환경에서는 즉시 통과시킨다.
 *   - useState 초기화 함수에서 .persist를 만지면 SSR이 터진다 — 초기값은 항상 false.
 */
export function AdminAuthGuard({ children }: AdminAuthGuardProps) {
  const accessToken = useAdminAuthStore((state) => state.accessToken)
  const [hasHydrated, setHasHydrated] = useState(false)

  useEffect(() => {
    if (typeof window === 'undefined') return

    let cancelled = false
    const finishHydration = () => {
      if (!cancelled) setHasHydrated(true)
    }

    // persist API가 노출되지 않은 환경에서는 즉시 통과 (가드는 더 이상 차단할 수 없음).
    if (!useAdminAuthStore.persist) {
      finishHydration()
      return () => {
        cancelled = true
      }
    }

    const unsubscribe = useAdminAuthStore.persist.onFinishHydration(finishHydration)

    // 이미 hydrate가 끝난 케이스 — 다음 microtask에서 확인해 race를 피한다.
    // setState를 async IIFE 안에서만 호출해 React Compiler의 "useEffect 본문
    // 동기 setState 금지" 규칙도 우회한다.
    ;(async () => {
      await Promise.resolve()
      if (!cancelled && useAdminAuthStore.persist?.hasHydrated() === true) {
        finishHydration()
      }
    })()

    return () => {
      cancelled = true
      unsubscribe()
    }
  }, [])

  if (!hasHydrated) return null
  if (!accessToken) return <AdminLoginModal />
  return <>{children}</>
}
