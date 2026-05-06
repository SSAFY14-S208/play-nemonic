'use client'

import { PHONE_COLORS } from '../constants'
import { usePhoneToast } from '../hooks'

export function PhoneToast() {
  const toastMessage = usePhoneToast()

  if (!toastMessage) return null

  return (
    <div
      className="body-b absolute left-1/2 top-1/2 z-[var(--z-toast)] w-[min(18rem,calc(100%-2rem))] -translate-x-1/2 -translate-y-1/2 rounded-[var(--radius-xl)] px-5 py-3 text-center text-fg-inverse shadow-lg"
      style={{ background: PHONE_COLORS.toastBackground }}
    >
      {toastMessage}
    </div>
  )
}
