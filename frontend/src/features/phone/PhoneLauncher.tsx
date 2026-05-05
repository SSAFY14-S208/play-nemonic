'use client'

import { Phone } from 'lucide-react'
import { cn } from '@/shared/libs'
import { PHONE_COLORS } from './constants'
import PhoneModal from './PhoneModal'
import { usePhoneStore } from './phoneStore'

export default function PhoneLauncher() {
  const isPhoneOpen = usePhoneStore((state) => state.isPhoneOpen)
  const openPhone = usePhoneStore((state) => state.openPhone)

  return (
    <>
      <button
        type="button"
        aria-label="핸드폰 열기"
        onClick={openPhone}
        className={cn(
          'fixed bottom-6 right-6 z-[var(--z-sticky)] flex h-16 w-16 items-center justify-center rounded-[1.25rem] bg-white shadow-[0_0.8rem_1.6rem_rgba(0,0,0,0.22)] transition duration-300 hover:-translate-y-1 focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-white',
          isPhoneOpen && 'pointer-events-none translate-y-3 opacity-0',
        )}
      >
        <span
          className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full border-2 border-white text-[0.62rem] font-bold leading-none text-white"
          style={{ background: PHONE_COLORS.launcherBadge }}
        >
          !
        </span>
        <span
          className="flex h-12 w-12 items-center justify-center rounded-[1rem]"
          style={{ background: PHONE_COLORS.launcherScreen }}
        >
          <Phone className="h-7 w-7 text-fg-primary" strokeWidth={2.4} />
        </span>
      </button>
      <PhoneModal />
    </>
  )
}
