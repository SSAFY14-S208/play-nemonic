import Image from 'next/image'

import { cn } from '@/shared/libs'

interface FlipbookDrawingIconButtonProps {
  imageSrc: string
  label: string
  pressed?: boolean
  onClick: () => void
}

interface SubmissionProgressBadgeProps {
  className?: string
  submittedCount: number
  totalCount: number
}

export function FlipbookDrawingIconButton({
  imageSrc,
  label,
  pressed,
  onClick,
}: FlipbookDrawingIconButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      title={label}
      className="relative grid size-14 place-items-center transition duration-150 hover:-translate-y-0.5 active:translate-y-px active:scale-95 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-flipbook-primary lg:size-[clamp(54px,4.6vw,70px)]"
      onClick={onClick}
    >
      <Image
        src={imageSrc}
        alt=""
        width={67}
        height={70}
        sizes="70px"
        className="h-full w-auto object-contain"
      />
      <span className="sr-only">{label}</span>
    </button>
  )
}

export function SubmissionProgressBadge({
  className,
  submittedCount,
  totalCount,
}: SubmissionProgressBadgeProps) {
  if (totalCount <= 0) return null

  return (
    <p
      aria-live="polite"
      className={cn(
        'body-b mx-auto inline-flex min-h-9 items-center justify-center rounded-full border border-[#ffd2df] bg-white/92 px-4 text-[#db4d82] shadow-[0_8px_18px_rgb(129_89_54_/_12%)]',
        className,
      )}
    >
      제출 {submittedCount}/{totalCount}명
    </p>
  )
}
