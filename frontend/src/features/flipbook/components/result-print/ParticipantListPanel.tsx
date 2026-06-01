import Image from 'next/image'
import { Sparkles } from 'lucide-react'

import { cn } from '@/shared/libs'

import {
  DEFAULT_ACCENT_COLORS,
  resultPrintStageStyles as styles,
} from './constants'
import type { FlipbookPrintParticipant } from './types'

interface ParticipantListPanelProps {
  participants: FlipbookPrintParticipant[]
  selectedParticipantIndex: number
  onSelectParticipant: (participantIndex: number) => void
}

export function ParticipantListPanel({
  participants,
  selectedParticipantIndex,
  onSelectParticipant,
}: ParticipantListPanelProps) {
  return (
    <>
      <div className={cn(styles.participantPanelHeader, 'relative z-10 flex h-[72px] items-start gap-2 pl-1')}>
        <Sparkles className="mt-0.5 size-6 stroke-[2.5] text-[#ffb84d]" aria-hidden />
        <p className="h2-b text-[#5d3b38]">
          참여자 목록
        </p>
      </div>

      <div className={cn(
        styles.participantListViewport,
        'relative z-10 grid flex-1 content-start gap-2 overflow-y-auto pr-1 [scrollbar-color:#ff9ab2_transparent] [scrollbar-width:thin]',
      )}>
        {participants.map((participant, participantIndex) => (
          <ParticipantListItem
            key={participant.id}
            participant={participant}
            participantIndex={participantIndex}
            isActive={participantIndex === selectedParticipantIndex}
            onSelectParticipant={onSelectParticipant}
          />
        ))}
      </div>
    </>
  )
}

function ParticipantListItem({
  participant,
  participantIndex,
  isActive,
  onSelectParticipant,
}: {
  participant: FlipbookPrintParticipant
  participantIndex: number
  isActive: boolean
  onSelectParticipant: (participantIndex: number) => void
}) {
  const accentColor = getParticipantAccentColor(participant, participantIndex)
  const thumbnailImageUrl = participant.frames.find((frame) => frame.imageUrl)?.imageUrl
  const printableFrameCount = getPrintableFrameCount(participant)

  return (
    <div
      className={cn(
        styles.participantListItem,
        'group relative grid min-h-[100px] grid-cols-[minmax(0,1fr)_auto] items-center gap-3 overflow-hidden rounded-[8px] border border-white/80 bg-white/86 px-3 py-3 text-left transition',
        'hover:bg-white',
        isActive && 'border-[#ff8aa4] bg-white',
      )}
    >
      <button
        type="button"
        onClick={() => onSelectParticipant(participantIndex)}
        className={cn(
          styles.participantSelectButton,
          'relative z-10 grid min-w-0 grid-cols-[76px_minmax(0,1fr)] items-center gap-3 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#ff8aa4] focus-visible:ring-offset-2 focus-visible:ring-offset-white',
        )}
        aria-label={participant.name + ' 결과 보기'}
        aria-pressed={isActive}
      >
        <ParticipantPaperThumbnail
          accentColor={accentColor}
          imageUrl={thumbnailImageUrl}
        />

        <span className="min-w-0">
          <span className="h3-b block truncate text-[#332222]">{participant.name}</span>
        </span>
      </button>

      <span className="relative z-10 grid justify-items-end gap-2">
        <span className="h4-b text-[#e56883]">{printableFrameCount}장</span>
      </span>
    </div>
  )
}

function ParticipantPaperThumbnail({
  accentColor,
  imageUrl,
}: {
  accentColor: string
  imageUrl?: string | null
}) {
  return (
    <span className="relative h-[64px] w-[70px]" aria-hidden>
      <span className="absolute left-1 top-2 h-[54px] w-[48px] rotate-[-7deg] rounded-[6px] border border-[#ffc5d3] bg-[#ffe9ef]" />
      <span className="absolute left-4 top-0 h-[58px] w-[50px] rotate-[4deg] overflow-hidden rounded-[6px] border border-white bg-white shadow-[0_5px_12px_rgb(120_80_80_/_12%)]">
        {imageUrl ? (
          <Image
            src={imageUrl}
            alt=""
            fill
            sizes="50px"
            unoptimized
            className="rounded-[6px] object-contain p-1"
          />
        ) : (
          <span
            className="absolute inset-1.5 rounded-[4px]"
            style={{ backgroundColor: accentColor + '55' }}
          />
        )}
        <span className="absolute inset-x-2 bottom-1.5 h-1 rounded-full bg-[#f2dca8]/80" />
      </span>
    </span>
  )
}

function getParticipantAccentColor(participant: FlipbookPrintParticipant, participantIndex: number) {
  return participant.accentColor ?? DEFAULT_ACCENT_COLORS[participantIndex % DEFAULT_ACCENT_COLORS.length]
}

function getPrintableFrameCount(participant: FlipbookPrintParticipant) {
  return participant.frames.filter((frame) => frame.outputMode !== 'gif-playback').length
}
