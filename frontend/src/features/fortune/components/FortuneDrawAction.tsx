import { cva, type VariantProps } from 'class-variance-authority'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

import { cn } from '@/shared/libs'

const drawActionVariants = cva(
  cn(
    'relative inline-flex items-center justify-center gap-[0.7rem] cursor-pointer border-0',
    'min-h-[clamp(8.8rem,14dvh,11.2rem)] min-w-[clamp(26rem,44vw,38rem)] px-[clamp(3rem,4.5vw,5rem)]',
    'bg-transparent bg-no-repeat bg-center bg-[length:100%_100%]',
    'font-fortune-eulyoo font-semibold text-[clamp(1.1rem,1.7vw,1.45rem)] tracking-[0.06em]',
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
