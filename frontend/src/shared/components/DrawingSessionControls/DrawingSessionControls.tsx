'use client'

import { Timer } from 'lucide-react'

import { cn } from '@/shared/libs'

type DrawingSessionTone = 'relay' | 'flipbook'

interface DrawingSessionControlsProps {
  remainingSeconds: number
  tone?: DrawingSessionTone
  onExit: () => void
}

const DRAWING_SESSION_TONE_STYLES = {
  relay: {
    exitButton: 'bg-relay-active text-relay-ink',
    timerBadge: 'bg-relay-ink text-fg-inverse',
  },
  flipbook: {
    exitButton: 'bg-flipbook-light text-flipbook-ink',
    timerBadge: 'bg-flipbook-timer text-fg-inverse',
  },
} as const

export function DrawingSessionControls({
  remainingSeconds,
  tone = 'relay',
  onExit,
}: DrawingSessionControlsProps) {
  const toneStyle = DRAWING_SESSION_TONE_STYLES[tone]

  return (
    <>
      <button
        type="button"
        onClick={onExit}
        className={cn(
          'body-b absolute left-[123px] top-16 min-h-[49px] rounded-full px-7',
          toneStyle.exitButton,
        )}
      >
        ‹ 나가기
      </button>

      <div
        className={cn(
          'body-b absolute left-[1136px] top-16 inline-flex min-h-[49px] min-w-[82px] items-center justify-center gap-2 rounded-full px-5',
          toneStyle.timerBadge,
        )}
      >
        <Timer className="size-5" aria-hidden />
        {remainingSeconds}
      </div>
    </>
  )
}
