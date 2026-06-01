import { cn } from '@/shared/libs'
import type { FlipbookPrintParticipant } from '@/features/flipbook/components/result-print'

interface MobileResultSelectorProps {
  participants: FlipbookPrintParticipant[]
  activeParticipantIndex: number
  onSelectParticipant: (participantIndex: number) => void
}

export function MobileResultSelector({
  participants,
  activeParticipantIndex,
  onSelectParticipant,
}: MobileResultSelectorProps) {
  if (participants.length === 0) return null

  return (
    <div className="absolute inset-x-3 bottom-[calc(0.75rem+env(safe-area-inset-bottom))] z-[120] grid max-h-[28svh] gap-2 rounded-[18px] border border-white/80 bg-white/86 px-3 pb-[calc(0.25rem+env(safe-area-inset-bottom))] pt-3 shadow-[0_14px_30px_rgb(120_80_80_/_16%)] backdrop-blur-md md:hidden">
      <p className="caption-b text-[#b84e66]">작품 선택</p>
      <div className="flex snap-x snap-mandatory gap-2 overflow-x-auto pb-1">
        {participants.map((participant, participantIndex) => {
          const isActiveParticipant = participantIndex === activeParticipantIndex

          return (
            <button
              key={participant.id}
              type="button"
              onClick={() => onSelectParticipant(participantIndex)}
              className={cn(
                'caption-b min-h-11 max-w-48 shrink-0 snap-start truncate rounded-full border px-4',
                isActiveParticipant
                  ? 'border-[#ff8aa4] bg-[#fff0f4] text-[#b84e66]'
                  : 'border-[#eadfd2] bg-white text-[#5d3b38]',
              )}
            >
              {participant.name}
            </button>
          )
        })}
      </div>
    </div>
  )
}
