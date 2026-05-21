'use client'

import { useEffect } from 'react'
import { usePathname } from 'next/navigation'
import { cn } from '@/shared/libs'
import { PhoneLauncherButton } from '@/shared/components'
import { useHubOnboardingStore, usePhoneLauncherStore } from '@/shared/stores'
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
      <PhoneLauncherButton
        className={cn(
          'fixed bottom-[calc(1.5rem+env(safe-area-inset-bottom))] right-[calc(1.5rem+env(safe-area-inset-right))] z-[14000] size-16 transition duration-300',
          isLauncherUnavailable && 'pointer-events-none translate-y-3 opacity-0',
        )}
        iconClassName="size-7"
      />
      <PhoneModal />
    </>
  )
}
