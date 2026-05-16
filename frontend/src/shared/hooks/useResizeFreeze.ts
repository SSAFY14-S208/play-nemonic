'use client'

import { useEffect } from 'react'

const RESIZING_CLASS = 'is-resizing'
const SETTLE_DELAY_MS = 200

export function useResizeFreeze() {
  useEffect(() => {
    let settleTimer: number | null = null

    const handleResize = () => {
      document.body.classList.add(RESIZING_CLASS)
      if (settleTimer !== null) window.clearTimeout(settleTimer)
      settleTimer = window.setTimeout(() => {
        document.body.classList.remove(RESIZING_CLASS)
        settleTimer = null
      }, SETTLE_DELAY_MS)
    }

    window.addEventListener('resize', handleResize, { passive: true })

    return () => {
      window.removeEventListener('resize', handleResize)
      if (settleTimer !== null) window.clearTimeout(settleTimer)
      document.body.classList.remove(RESIZING_CLASS)
    }
  }, [])
}
