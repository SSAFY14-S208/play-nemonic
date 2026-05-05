import type { ReactNode } from 'react'
import Image from 'next/image'
import { phoneDeviceFrame } from '@/shared/assets'
import { PHONE_COLORS } from '../constants'
import { PhoneStatusBar } from './PhoneStatusBar'

interface PhoneFrameProps {
  children: ReactNode
  statusBarVariant: 'light' | 'dark'
}

export function PhoneFrame({ children, statusBarVariant }: PhoneFrameProps) {
  return (
    <div className="relative aspect-[498/1024] w-[min(393px,calc(100vw-1.5rem))] max-h-[calc(100dvh-2rem)] max-w-[calc((100dvh-2rem)*0.486)]">
      <Image
        src={phoneDeviceFrame}
        alt=""
        aria-hidden
        fill
        priority
        className="pointer-events-none object-fill"
        sizes="393px"
      />

      <div
        className="absolute inset-[0.38rem] overflow-hidden rounded-[2.35rem]"
        style={{
          background: PHONE_COLORS.black,
        }}
      >
        <div className="absolute inset-[0.38rem] overflow-hidden rounded-[2rem] bg-surface-default shadow-[inset_0_0_0_1px_rgba(0,0,0,0.08)]">
          <PhoneStatusBar variant={statusBarVariant} />
          {children}
          <div
            className="pointer-events-none absolute bottom-3 left-1/2 z-20 h-1 w-32 -translate-x-1/2 rounded-full"
            style={{ background: PHONE_COLORS.screenHandle }}
          />
        </div>
      </div>
    </div>
  )
}
