'use client'

import type { MouseEventHandler } from 'react'

import { cn } from '@/shared/libs'

interface FortuneBgmToggleProps {
  isMuted: boolean
  onToggle: MouseEventHandler<HTMLButtonElement>
}

const POSITION =
  'fixed top-[calc(env(safe-area-inset-top)+clamp(0.65rem,1.6vw,1.15rem))] right-[calc(env(safe-area-inset-right)+clamp(0.55rem,1.7vw,1.15rem))] z-20'

const BASE = cn(
  POSITION,
  'aspect-square w-[clamp(5.2rem,7.4vw,7.4rem)] p-0 border-0 cursor-pointer rounded-[1.4rem]',
  "bg-transparent bg-no-repeat bg-center bg-contain bg-[url('/images/fortune/stage/bgm-on-button.png')]",
  "data-[muted]:bg-[url('/images/fortune/stage/bgm-muted-button.png')]",
  '[filter:drop-shadow(0_0_0.76rem_rgba(157,80,255,0.34))_drop-shadow(0_0.48rem_0.92rem_rgba(4,1,12,0.46))]',
  '[transition:filter_180ms_ease,opacity_180ms_ease,transform_180ms_ease]',
  'hover:[filter:drop-shadow(0_0_1rem_rgba(218,144,255,0.62))_drop-shadow(0_0.54rem_1rem_rgba(4,1,12,0.5))_saturate(1.08)_brightness(1.04)]',
  'hover:translate-y-[-0.08rem] hover:scale-[1.035]',
  'active:translate-y-[0.02rem] active:scale-[0.985]',
  'focus-visible:outline-2 focus-visible:outline-[rgba(255,236,183,0.96)] focus-visible:-outline-offset-[0.15rem]',
  'motion-reduce:transition-none',
)

export default function FortuneBgmToggle({ isMuted, onToggle }: FortuneBgmToggleProps) {
  const label = isMuted ? '타로 배경음악 켜기' : '타로 배경음악 음소거'

  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={isMuted}
      data-muted={isMuted || undefined}
      className={cn(BASE)}
      title={isMuted ? '배경음악 켜기' : '배경음악 음소거'}
      onClick={onToggle}
      onKeyDown={(event) => event.stopPropagation()}
      onPointerDown={(event) => event.stopPropagation()}
    >
      <span className="sr-only">{label}</span>
    </button>
  )
}
