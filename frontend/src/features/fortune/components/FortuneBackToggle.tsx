'use client'

import { ChevronLeft } from 'lucide-react'
import type { MouseEventHandler } from 'react'

import { cn } from '@/shared/libs'

interface FortuneBackToggleProps {
  onClick: MouseEventHandler<HTMLButtonElement>
  inline?: boolean
}

const FIXED_POSITION =
  'fixed top-[calc(env(safe-area-inset-top)+clamp(1.4rem,2.8vw,2.2rem))] left-[calc(env(safe-area-inset-left)+clamp(1.4rem,2.8vw,2.2rem))] z-20'

const BASE = cn(
  'inline-flex items-center justify-center gap-[0.32rem] cursor-pointer border-0',
  'aspect-[500/300] h-[clamp(2.6rem,4.2vw,3.1rem)] p-0',
  "bg-transparent bg-no-repeat bg-center bg-[length:100%_100%] bg-[url('/images/fortune/form/birth-label-plaque.png')]",
  'text-[rgba(255,244,214,0.96)] font-fortune-serif text-[clamp(0.58rem,1.1vw,0.78rem)] font-extrabold',
  '[text-shadow:0_0.12rem_0.24rem_rgba(9,1,20,0.8),0_0_0.45rem_rgba(215,130,255,0.48)]',
  '[filter:drop-shadow(0_0_0.76rem_rgba(157,80,255,0.34))_drop-shadow(0_0.48rem_0.92rem_rgba(4,1,12,0.46))]',
  '[transition:filter_180ms_ease,transform_180ms_ease]',
  'hover:[filter:drop-shadow(0_0_1rem_rgba(218,144,255,0.62))_drop-shadow(0_0.54rem_1rem_rgba(4,1,12,0.5))_saturate(1.08)_brightness(1.04)]',
  'hover:translate-y-[-0.08rem] hover:scale-[1.025]',
  'active:translate-y-[0.02rem] active:scale-[0.985]',
  'focus-visible:outline-2 focus-visible:outline-[rgba(255,236,183,0.96)] focus-visible:-outline-offset-[0.15rem]',
  'motion-reduce:transition-none',
)

export default function FortuneBackToggle({ onClick, inline = false }: FortuneBackToggleProps) {
  return (
    <button
      type="button"
      aria-label="이전 화면으로 돌아가기"
      className={cn(BASE, !inline && FIXED_POSITION)}
      onClick={onClick}
    >
      <ChevronLeft className="size-3" aria-hidden />
      <span className="relative z-1">뒤로</span>
    </button>
  )
}
