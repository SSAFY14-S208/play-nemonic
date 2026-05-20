'use client'

import { useEffect } from 'react'
import { Phone } from 'lucide-react'
import { usePathname } from 'next/navigation'
import { cn } from '@/shared/libs'
import { useHubOnboardingStore, usePhoneLauncherStore } from '@/shared/stores'
import { PHONE_COLORS } from './constants'
import PhoneModal from './PhoneModal'
import { usePhoneStore } from './phoneStore'

const NEMONIC_ROUTE_PREFIX = '/nemonic'

export default function PhoneLauncher() {
  const pathname = usePathname()
  const isPhoneOpen = usePhoneStore((state) => state.isPhoneOpen)
  const openPhone = usePhoneStore((state) => state.openPhone)
  const isHubOnboardingActive = useHubOnboardingStore(
    (state) =>
      state.hasHydratedFromStorage &&
      state.hasEnteredHub &&
      !state.hasSeenOnboarding,
  )
  const isLauncherHidden = usePhoneLauncherStore(
    (state) => state.isLauncherHidden,
  )

  // 다른 feature가 shared store를 통해 phone을 열 수 있도록 콜백 등록
  const registerOpenPhone = usePhoneLauncherStore(
    (state) => state.registerOpenPhone,
  )
  useEffect(() => {
    registerOpenPhone(openPhone)
  }, [registerOpenPhone, openPhone])

  const isNemonicRoute =
    pathname === NEMONIC_ROUTE_PREFIX ||
    pathname.startsWith(`${NEMONIC_ROUTE_PREFIX}/`)
  const isLauncherUnavailable =
    isPhoneOpen || isHubOnboardingActive || isLauncherHidden
  const shouldShowGalleryPrintHint = isNemonicRoute && !isLauncherUnavailable

  return (
    <>
      {shouldShowGalleryPrintHint && (
        <p
          aria-live="polite"
          className="caption-b pointer-events-none fixed bottom-[calc(5.85rem+env(safe-area-inset-bottom))] right-[calc(1.25rem+env(safe-area-inset-right))] z-[13990] max-w-[13.5rem] rounded-[var(--radius-md)] border border-border-default/70 bg-surface-default/92 px-3.5 py-2 text-center text-fg-primary shadow-[0_0.75rem_1.6rem_rgb(71_68_112_/_18%)] backdrop-blur-md"
        >
          갤러리에서 출력해보세요!
          <span className="absolute -bottom-1.5 right-8 h-3 w-3 rotate-45 border-b border-r border-border-default/70 bg-surface-default/92" />
        </p>
      )}
      <button
        type="button"
        aria-label="핸드폰 열기"
        onClick={openPhone}
        className={cn(
          'fixed bottom-[calc(1.5rem+env(safe-area-inset-bottom))] right-[calc(1.5rem+env(safe-area-inset-right))] z-[14000] flex h-16 w-16 items-center justify-center rounded-[1.25rem] bg-white transition duration-300 hover:-translate-y-1 focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-white',
          isLauncherUnavailable && 'pointer-events-none translate-y-3 opacity-0',
        )}
        style={{ boxShadow: PHONE_COLORS.launcherShadow }}
      >
        <span
          className="phone-launcher-badge absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full border-2 border-white text-white"
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
