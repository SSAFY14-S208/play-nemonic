import Image from 'next/image'
import { cn } from '@/shared/libs'
import { PHONE_DRAWING_LAYOUT } from '../../constants'
import { DRAWING_ACTION_BUTTONS } from './constants'

interface DrawingActionButtonRowProps {
  onCreateArtifact: (action: 'save' | 'print') => void | Promise<void>
  isPrinting?: boolean
  isSaving?: boolean
}

export function DrawingActionButtonRow({
  isPrinting = false,
  onCreateArtifact,
  isSaving = false,
}: DrawingActionButtonRowProps) {
  return (
    <footer
      className="absolute z-20"
      style={PHONE_DRAWING_LAYOUT.actionButtonRow}
    >
      {DRAWING_ACTION_BUTTONS.map((actionButton) => {
        const isActionSaving = actionButton.action === 'save' && isSaving
        const isActionPrinting = actionButton.action === 'print' && isPrinting
        const isDisabled = isSaving || isPrinting
        const actionLabel = isActionSaving
          ? '저장 중...'
          : isActionPrinting
            ? '준비 중...'
            : actionButton.label

        return (
          <button
            key={actionButton.action}
            type="button"
            disabled={isDisabled}
            onClick={() => {
              void onCreateArtifact(actionButton.action)
            }}
            className={cn(
              'phone-drawing-action-label flex h-full w-full items-center justify-center gap-[4.1%] transition hover:-translate-y-0.5',
              isDisabled && 'pointer-events-none opacity-60',
            )}
            style={actionButton.style}
          >
            <span className={cn('relative block', actionButton.iconClassName)}>
              <Image
                src={actionButton.icon}
                alt=""
                aria-hidden
                fill
                className="object-contain"
                sizes="28px"
              />
            </span>
            <span className="text-center">{actionLabel}</span>
          </button>
        )
      })}
    </footer>
  )
}
