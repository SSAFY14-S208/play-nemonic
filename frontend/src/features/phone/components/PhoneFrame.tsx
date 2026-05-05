import type { ReactNode } from 'react'
import { PhoneStatusBar } from './PhoneStatusBar'

interface PhoneFrameProps {
  children: ReactNode
  statusBarVariant: 'light' | 'dark'
}

export function PhoneFrame({ children, statusBarVariant }: PhoneFrameProps) {
  return (
    <div className="relative h-[min(812px,calc(100dvh-2rem))] w-[min(375px,calc(100vw-1.5rem))]">
      <div className="absolute right-[-0.35rem] top-[11rem] h-20 w-1 rounded-r-[0.35rem] bg-[#bfc8da]" />
      <div className="absolute left-[-0.35rem] top-[8rem] h-10 w-1 rounded-l-[0.35rem] bg-[#bfc8da]" />
      <div className="absolute left-[-0.35rem] top-[13rem] h-16 w-1 rounded-l-[0.35rem] bg-[#bfc8da]" />

      <div className="relative h-full overflow-hidden rounded-[2.75rem] border-[0.38rem] border-[#11151d] bg-[#11151d] shadow-[0_1.5rem_3rem_rgba(0,0,0,0.32),inset_0_0_0_1px_rgba(255,255,255,0.38)]">
        <div className="absolute inset-[0.38rem] overflow-hidden rounded-[2.28rem] bg-surface-default shadow-[inset_0_0_0_1px_rgba(0,0,0,0.08)]">
          <PhoneStatusBar variant={statusBarVariant} />
          {children}
          <div className="pointer-events-none absolute bottom-3 left-1/2 z-20 h-1 w-32 -translate-x-1/2 rounded-full bg-[rgba(70,63,78,0.72)]" />
        </div>
      </div>
    </div>
  )
}
