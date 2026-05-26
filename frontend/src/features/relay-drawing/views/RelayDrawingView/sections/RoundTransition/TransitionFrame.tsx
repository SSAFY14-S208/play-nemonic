'use client'

import { cn } from '@/shared/libs'
import { RELAY_ROUND_ORDER, RELAY_ROUNDS, type RelayRoundKey } from '@/features/relay-drawing/constants'
import TransitionSticker from './TransitionSticker'

interface StickerEntry {
  roundKey: RelayRoundKey
  imageUrl: string
}

interface TransitionFrameProps {
  completedRoundKey: RelayRoundKey | null
  nextRoundKey: RelayRoundKey | null
  previousStickers: StickerEntry[]
  stickerImageUrl: string | null
  showSticker: boolean
}

type SlotState = 'completed' | 'landing' | 'next' | 'empty'

function getSlotState(
  slotRoundKey: RelayRoundKey,
  completedRoundKey: RelayRoundKey | null,
  nextRoundKey: RelayRoundKey | null,
  previousStickers: StickerEntry[],
): SlotState {
  if (previousStickers.some((sticker) => sticker.roundKey === slotRoundKey)) {
    return 'completed'
  }
  if (slotRoundKey === completedRoundKey) {
    return 'landing'
  }
  if (slotRoundKey === nextRoundKey) {
    return 'next'
  }
  return 'empty'
}

export default function TransitionFrame({
  completedRoundKey,
  nextRoundKey,
  previousStickers,
  stickerImageUrl,
  showSticker,
}: TransitionFrameProps) {
  return (
    <div className="flex h-[640px] w-[283px] flex-col rounded-2xl border-2 border-relay-line bg-relay-paper shadow-lg">
      {RELAY_ROUND_ORDER.map((roundKey, index) => {
        const slotState = getSlotState(roundKey, completedRoundKey, nextRoundKey, previousStickers)
        const round = RELAY_ROUNDS[index]
        const previousSticker = previousStickers.find(
          (sticker) => sticker.roundKey === roundKey,
        )

        return (
          <div
            key={roundKey}
            className={cn(
              'relative flex flex-1 items-center justify-center',
              index < RELAY_ROUND_ORDER.length - 1 && 'border-b border-relay-line',
              slotState === 'next' && 'border-2 border-dashed border-relay-accent-strong',
            )}
          >
            {slotState === 'completed' && previousSticker && (
              <TransitionSticker
                roundKey={roundKey}
                imageUrl={previousSticker.imageUrl}
                isLanding={false}
                slotIndex={index}
              />
            )}

            {slotState === 'landing' && showSticker && stickerImageUrl && (
              <TransitionSticker
                roundKey={roundKey}
                imageUrl={stickerImageUrl}
                isLanding
                slotIndex={index}
              />
            )}

            {slotState === 'next' && (
              <span className="caption-b text-relay-accent-strong">다음 차례</span>
            )}

            {slotState === 'empty' && (
              <span className="caption-r text-relay-muted">{round.label}</span>
            )}
          </div>
        )
      })}
    </div>
  )
}
