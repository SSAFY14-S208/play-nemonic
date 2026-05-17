import Image from 'next/image'
import { X } from 'lucide-react'
import { phoneClose } from '@/shared/assets'
import { PHONE_CLOSE_BUTTON_LAYOUT } from '../constants'

interface PhoneCloseButtonProps {
  onClose: () => void
}

export function PhoneCloseButton({ onClose }: PhoneCloseButtonProps) {
  return (
    <button
      type="button"
      aria-label="휴대폰 닫기"
      onClick={onClose}
      className="absolute z-40 transition hover:scale-[1.02] focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2 max-sm:!left-auto max-sm:!right-[4%] max-sm:!top-[4%] max-sm:!h-12 max-sm:!w-12 max-sm:grid max-sm:place-items-center max-sm:rounded-full max-sm:border max-sm:border-border-default max-sm:bg-surface-subtle max-sm:shadow-[0_10px_24px_rgb(0_0_0_/_14%)]"
      style={PHONE_CLOSE_BUTTON_LAYOUT}
    >
      <X className="hidden size-5 text-fg-secondary max-sm:block" aria-hidden />
      <Image
        src={phoneClose}
        alt=""
        aria-hidden
        fill
        className="object-contain max-sm:hidden"
        sizes="90px"
      />
    </button>
  )
}
