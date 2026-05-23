'use client'

import { useEffect } from 'react'
import { usePhoneStore } from '..'

export function usePhoneToast() {
  const dismissToast = usePhoneStore((state) => state.dismissToast)
  const toastMessage = usePhoneStore((state) => state.toastMessage)

  useEffect(() => {
    if (!toastMessage) return

    const toastTimer = window.setTimeout(() => {
      dismissToast()
    }, 2200)

    return () => {
      window.clearTimeout(toastTimer)
    }
  }, [dismissToast, toastMessage])

  return toastMessage
}
