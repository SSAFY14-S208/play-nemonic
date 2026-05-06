import Image from 'next/image'
import { cn } from '@/shared/libs'
import { PHONE_DRAWING_LAYOUT } from '../../constants'
import { DRAWING_ACTION_BUTTONS } from './constants'

interface DrawingActionButtonRowProps {
  onCreateArtifact: (action: 'save' | 'print') => void
}

export function DrawingActionButtonRow({
  onCreateArtifact,
}: DrawingActionButtonRowProps) {
  return (
    <footer
      className="absolute z-20"
      style={PHONE_DRAWING_LAYOUT.actionButtonRow}
    >
      {DRAWING_ACTION_BUTTONS.map((actionButton) => (
        <button
          key={actionButton.action}
          type="button"
          onClick={() => onCreateArtifact(actionButton.action)}
          className="phone-drawing-action-label flex h-full w-full items-center justify-center gap-[4.1%] transition hover:-translate-y-0.5"
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
          <span className="text-center">{actionButton.label}</span>
        </button>
      ))}
    </footer>
  )
}
