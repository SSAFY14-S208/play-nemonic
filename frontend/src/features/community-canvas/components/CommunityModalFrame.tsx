'use client'

import type { CSSProperties, ReactNode } from 'react'
import { X } from 'lucide-react'
import { cn } from '@/shared/libs'

type CommunityModalFrameStyle = CSSProperties & Record<`--${string}`, string>

interface CommunityModalFrameProps {
  isOpen: boolean
  titleId: string
  frameImageUrl: string
  aspectRatio: number
  maxWidth: string
  overlayClassName?: string
  frameClassName?: string
  contentClassName?: string
  closeButtonClassName?: string
  closeButtonLabel?: string
  onClose: () => void
  children: ReactNode
}

export function CommunityModalFrame({
  isOpen,
  titleId,
  frameImageUrl,
  aspectRatio,
  maxWidth,
  overlayClassName,
  frameClassName,
  contentClassName,
  closeButtonClassName,
  closeButtonLabel = '팝업 닫기',
  onClose,
  children,
}: CommunityModalFrameProps) {
  if (!isOpen) return null

  const frameStyle: CommunityModalFrameStyle = {
    '--community-modal-aspect-ratio': `${aspectRatio}`,
    width: `min(96vw, ${maxWidth}, calc((100dvh - 2rem) * ${aspectRatio}))`,
    backgroundImage: `url("${frameImageUrl}")`,
    backgroundSize: '100% 100%',
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      className={cn(
        'fixed inset-0 z-[var(--z-overlay)] grid place-items-center overflow-y-auto bg-[#19172a]/50 p-4 backdrop-blur-[2px]',
        overlayClassName,
      )}
    >
      <section
        className={cn(
          'relative aspect-[var(--community-modal-aspect-ratio)] overflow-hidden bg-contain bg-center bg-no-repeat',
          frameClassName,
        )}
        style={frameStyle}
      >
        <button
          type="button"
          aria-label={closeButtonLabel}
          onClick={onClose}
          className={cn(
            'absolute right-[5%] top-[5%] z-20 grid size-11 place-items-center rounded-full border border-[#ffd66b] bg-[#fff8e1] text-fg-secondary shadow-[0_7px_16px_rgb(71_68_112_/_16%)] transition hover:-translate-y-0.5 hover:bg-white',
            closeButtonClassName,
          )}
        >
          <X className="size-5" />
        </button>

        <div className={cn('absolute min-h-0 overflow-hidden', contentClassName)}>
          {children}
        </div>
      </section>
    </div>
  )
}
