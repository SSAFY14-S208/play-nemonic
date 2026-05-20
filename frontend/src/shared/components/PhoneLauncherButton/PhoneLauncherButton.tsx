'use client'

import { Phone } from 'lucide-react'

import { cn } from '@/shared/libs'
import { usePhoneLauncherStore } from '@/shared/stores'

// PhoneLauncher(features/phone)의 디자인을 그대로 재현한 색상 상수.
// cross-feature import 없이 값을 직접 선언한다.
const LAUNCHER_SCREEN_GRADIENT =
  'linear-gradient(135deg,#dff4ff 0%,#ffe6f2 100%)'
const LAUNCHER_SHADOW = '0 0.8rem 1.6rem rgba(0, 0, 0, 0.22)'
const LAUNCHER_BADGE_COLOR = '#ff6f7b'

/** 인라인으로 배치되는 phone 열기 버튼.
 *  원본 PhoneLauncher 버튼과 동일한 디자인.
 *  shared/stores/phoneLauncherStore를 통해 PhoneLauncher의 openPhone을 호출한다. */
export default function PhoneLauncherButton({
  className,
}: {
  className?: string
}) {
  const requestOpen = usePhoneLauncherStore((state) => state.requestOpen)

  return (
    <button
      type="button"
      onClick={requestOpen}
      aria-label="핸드폰 열기"
      className={cn(
        'relative inline-flex size-16 cursor-pointer items-center justify-center rounded-[1.25rem] bg-white transition duration-300 hover:-translate-y-1 active:scale-95',
        className,
      )}
      style={{ boxShadow: LAUNCHER_SHADOW }}
    >
      <span
        className="absolute -right-1 -top-1 flex size-5 items-center justify-center rounded-full border-2 border-white text-[10px] font-bold text-white"
        style={{ background: LAUNCHER_BADGE_COLOR }}
      >
        !
      </span>
      <span
        className="flex size-12 items-center justify-center rounded-[1rem]"
        style={{ background: LAUNCHER_SCREEN_GRADIENT }}
      >
        <Phone className="size-7 text-fg-primary" strokeWidth={2.4} />
      </span>
    </button>
  )
}
