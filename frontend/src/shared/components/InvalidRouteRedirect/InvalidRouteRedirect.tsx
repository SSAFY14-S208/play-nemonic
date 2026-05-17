'use client'

import { useCallback, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { AlertTriangle, Home } from 'lucide-react'
import { toast } from 'sonner'

const INVALID_ACCESS_TITLE = '\uc798\ubabb\ub41c \ud398\uc774\uc9c0 \uc811\uadfc\uc785\ub2c8\ub2e4.'
const INVALID_ACCESS_DESCRIPTION =
  '\uc9c0\uc6d0\ud558\uc9c0 \uc54a\ub294 \uc8fc\uc18c\uc774\uac70\ub098 \ub9cc\ub8cc\ub41c \ub9c1\ud06c\uc77c \uc218 \uc788\uc5b4\uc694.'
const REDIRECT_NOTICE = '\uc7a0\uc2dc \ud6c4 \ud5c8\ube0c\ub85c \uc774\ub3d9\ud569\ub2c8\ub2e4.'
const REDIRECT_BUTTON_LABEL = '\ud5c8\ube0c\ub85c \uc774\ub3d9'
const INVALID_ROUTE_TOAST_ID = 'invalid-route-redirect'
const REDIRECT_DELAY_MS = 1800

interface InvalidRouteRedirectProps {
  redirectPath?: string
}

export function InvalidRouteRedirect({
  redirectPath = '/hub',
}: InvalidRouteRedirectProps) {
  const router = useRouter()

  const redirectToFallbackPage = useCallback(() => {
    router.replace(redirectPath)
  }, [redirectPath, router])

  useEffect(() => {
    toast.error(`${INVALID_ACCESS_TITLE} ${REDIRECT_NOTICE}`, {
      id: INVALID_ROUTE_TOAST_ID,
    })

    const redirectTimer = window.setTimeout(redirectToFallbackPage, REDIRECT_DELAY_MS)

    return () => {
      window.clearTimeout(redirectTimer)
    }
  }, [redirectToFallbackPage])

  return (
    <main className="flex min-h-dvh items-center justify-center bg-surface-default px-6 py-12">
      <section
        aria-live="polite"
        className="flex w-full max-w-[28rem] flex-col items-center text-center"
      >
        <div className="mb-6 grid size-14 place-items-center rounded-full bg-surface-subtle text-fg-secondary">
          <AlertTriangle className="size-7" aria-hidden />
        </div>
        <h1 className="h3-b text-fg-primary">{INVALID_ACCESS_TITLE}</h1>
        <p className="body-r mt-3 text-fg-secondary">{INVALID_ACCESS_DESCRIPTION}</p>
        <p className="body-b mt-2 text-fg-primary">{REDIRECT_NOTICE}</p>
        <button
          type="button"
          onClick={redirectToFallbackPage}
          className="body-b mt-8 inline-flex h-11 items-center justify-center gap-2 rounded-[var(--radius-md)] bg-primary-1 px-5 text-fg-inverse transition hover:-translate-y-0.5"
        >
          <Home className="size-4" aria-hidden />
          {REDIRECT_BUTTON_LABEL}
        </button>
      </section>
    </main>
  )
}
