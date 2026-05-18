import { X } from 'lucide-react'

interface PhoneMobileCloseButtonProps {
  onClose: () => void
}

export function PhoneMobileCloseButton({ onClose }: PhoneMobileCloseButtonProps) {
  return (
    <button
      type="button"
      aria-label="휴대폰 닫기"
      onClick={onClose}
      className="fixed right-5 top-5 z-[var(--z-modal)] hidden h-12 w-12 place-items-center rounded-full border border-border-default bg-surface-subtle shadow-[0_10px_24px_rgb(0_0_0/14%)] transition hover:scale-[1.02] focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2 max-sm:grid"
    >
      <X className="size-5 text-fg-secondary" aria-hidden />
    </button>
  )
}
