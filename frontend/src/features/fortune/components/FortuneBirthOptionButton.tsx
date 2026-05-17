import { cva, type VariantProps } from 'class-variance-authority'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

import { cn } from '@/shared/libs'

const birthOptionVariants = cva(
  cn(
    'relative z-1 flex w-full min-h-full items-center justify-center gap-[clamp(0.34rem,0.9vw,0.54rem)]',
    'border-0 bg-transparent cursor-pointer',
    'font-fortune-serif text-[clamp(1.05rem,2vw,1.28rem)] [font-weight:850] tracking-normal',
    '[text-shadow:0_0.1rem_0.28rem_rgba(5,1,16,0.72)]',
    '[transition:color_200ms_ease,filter_200ms_ease]',
    'hover:-translate-y-[0.04rem]',
    'motion-reduce:transition-none',
  ),
  {
    variants: {
      state: {
        active:
          'text-[rgba(255,249,226,0.98)] [filter:drop-shadow(0_0_0.44rem_rgba(223,136,255,0.46))]',
        idle: cn(
          'text-[rgba(255,245,218,0.82)]',
          'hover:text-[rgba(255,249,226,0.96)] hover:[filter:drop-shadow(0_0_0.32rem_rgba(223,136,255,0.32))]',
        ),
      },
    },
    defaultVariants: { state: 'idle' },
  },
)

interface FortuneBirthOptionButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof birthOptionVariants> {
  icon: ReactNode
}

export default function FortuneBirthOptionButton({
  state,
  icon,
  className,
  children,
  ...buttonProps
}: FortuneBirthOptionButtonProps) {
  return (
    <button
      type="button"
      className={cn(birthOptionVariants({ state }), className)}
      {...buttonProps}
    >
      {icon}
      {children}
    </button>
  )
}
