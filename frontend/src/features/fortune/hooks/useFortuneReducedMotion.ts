import { useEffect, useState } from 'react'

export function useFortuneReducedMotion() {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false)

  useEffect(() => {
    let cancelled = false
    let mediaQuery: MediaQueryList | null = null

    const handleChange = () => {
      if (!cancelled && mediaQuery) {
        setPrefersReducedMotion(mediaQuery.matches)
      }
    }

    ;(async () => {
      await Promise.resolve()

      if (cancelled) {
        return
      }

      mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')

      setPrefersReducedMotion(mediaQuery.matches)
      mediaQuery.addEventListener('change', handleChange)
    })()

    return () => {
      cancelled = true
      mediaQuery?.removeEventListener('change', handleChange)
    }
  }, [])

  return prefersReducedMotion
}
