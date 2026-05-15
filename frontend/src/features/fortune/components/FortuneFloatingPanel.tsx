import type { HTMLAttributes } from 'react'

import { cn } from '@/shared/libs'

type FortuneFloatingPanelProps = HTMLAttributes<HTMLElement>

const PANEL_BACKGROUND =
  'bg-[linear-gradient(180deg,rgba(255,255,255,0.78),rgba(255,250,234,0.94)),var(--color-fortune-panel)]'

const PANEL_SHADOW =
  'shadow-[0_1.1rem_2.7rem_rgba(64,26,83,0.2),0_0_0_0.34rem_rgba(214,190,255,0.14),inset_0_0.1rem_0_rgba(255,255,255,0.88)]'

const HALO_BEFORE = cn(
  "before:absolute before:left-1/2 before:-top-[4.8rem] before:h-[7rem] before:w-[82%] before:-translate-x-1/2",
  "before:rounded-full before:pointer-events-none before:content-['']",
  'before:bg-[linear-gradient(180deg,rgba(255,236,168,0.3),rgba(255,236,168,0))]',
)

export default function FortuneFloatingPanel({ className, children, ...props }: FortuneFloatingPanelProps) {
  return (
    <section
      className={cn(
        'relative overflow-hidden border-2 border-fortune-border rounded-[var(--radius-xl)] text-center',
        PANEL_BACKGROUND,
        PANEL_SHADOW,
        HALO_BEFORE,
        className,
      )}
      {...props}
    >
      {children}
    </section>
  )
}
