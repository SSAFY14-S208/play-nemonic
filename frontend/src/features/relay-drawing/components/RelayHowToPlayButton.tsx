'use client'

import Image from 'next/image'
import { useState } from 'react'

import { HowToPlayModal } from '@/shared/components'
import { cn } from '@/shared/libs'

import helpIcon from '../assets/how-to-play.png'
import { RELAY_HOW_TO_PLAY_PANELS } from '../constants'

export default function RelayHowToPlayButton({ className }: { className?: string }) {
  const [isHowToPlayModalOpen, setIsHowToPlayModalOpen] = useState(false)

  return (
    <>
      <button
        type="button"
        onClick={() => setIsHowToPlayModalOpen(true)}
        aria-label="게임 설명 열기"
        title="게임 설명"
        className={cn(
          'inline-flex size-16 cursor-pointer items-center justify-center bg-transparent transition hover:-translate-y-0.5 hover:brightness-105 active:scale-95',
          className,
        )}
      >
        <Image
          src={helpIcon}
          alt=""
          aria-hidden
          width={64}
          height={64}
          className="size-16 object-contain"
        />
      </button>

      <HowToPlayModal
        open={isHowToPlayModalOpen}
        onOpenChange={setIsHowToPlayModalOpen}
        panels={RELAY_HOW_TO_PLAY_PANELS}
        accentColor="var(--color-relay-accent)"
      />
    </>
  )
}
