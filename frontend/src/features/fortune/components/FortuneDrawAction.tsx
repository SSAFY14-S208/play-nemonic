import { cva, type VariantProps } from 'class-variance-authority'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

import { cn } from '@/shared/libs'

const drawActionVariants = cva(
  cn(
    'relative inline-flex items-center justify-center gap-[clamp(0.32rem,0.6vw,0.7rem)] cursor-pointer border-0',
    // Both buttons use the same width + 4:1 aspect (matches the 800×200 PNGs),
    // so the two buttons are always identical in size regardless of label length.
    'w-[clamp(9rem,30vw,22rem)] aspect-[800/200] px-[clamp(0.7rem,2vw,1.6rem)]',
    // mobile portrait — bump up to ~44vw each so the row uses most of the viewport
    'max-[767px]:portrait:w-[44vw] max-[767px]:portrait:px-[1rem]',
    'bg-transparent bg-no-repeat bg-center bg-[length:100%_100%]',
    'font-fortune-eulyoo font-semibold tracking-[0.04em]',
    'text-[clamp(0.78rem,1.5vw,1.3rem)] max-[767px]:portrait:text-[clamp(0.95rem,3.4vw,1.15rem)] whitespace-nowrap',
    '[text-shadow:0_0_0.4rem_rgba(180,110,255,0.55),0_0_0.18rem_rgba(255,255,255,0.5)]',
    '[transition:transform_220ms_ease,filter_220ms_ease]',
    'hover:-translate-y-0.5 hover:[filter:brightness(1.08)_drop-shadow(0_0_0.6rem_rgba(220,170,255,0.55))]',
    'disabled:cursor-not-allowed disabled:opacity-70 disabled:translate-y-0 disabled:[filter:none]',
    'motion-reduce:transition-none',
  ),
  {
    variants: {
      tone: {
        edit: "bg-[url('/images/fortune/draw/action-edit.png')] text-[#f5e8ff]",
        print: "bg-[url('/images/fortune/draw/action-print.png')] text-[#fff8ff]",
      },
    },
    defaultVariants: { tone: 'edit' },
  },
)

interface FortuneDrawActionProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof drawActionVariants> {
  icon: ReactNode
}

export default function FortuneDrawAction({
  tone,
  icon,
  className,
  children,
  ...buttonProps
}: FortuneDrawActionProps) {
  return (
    <button type="button" className={cn(drawActionVariants({ tone }), className)} {...buttonProps}>
      {icon}
      <span>{children}</span>
    </button>
  )
}
