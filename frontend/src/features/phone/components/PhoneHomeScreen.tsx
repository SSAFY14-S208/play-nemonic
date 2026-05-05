import Link from 'next/link'
import Image from 'next/image'
import { phoneIconEdit, phoneProfileAvatar } from '@/shared/assets'
import { cn } from '@/shared/libs'
import {
  PHONE_APP_SHORTCUTS,
  PHONE_COLORS,
  PHONE_PROFILE,
} from '../constants'

interface PhoneHomeScreenProps {
  onOpenDrawing: () => void
  onOpenGallery: () => void
}

export function PhoneHomeScreen({
  onOpenDrawing,
  onOpenGallery,
}: PhoneHomeScreenProps) {
  return (
    <div className="phone-home-body-m flex h-full flex-col bg-surface-default">
      <section
        className="h-[10.5rem] px-[1.375rem] pt-[4.15rem] text-white"
        style={{ background: PHONE_COLORS.homeHeader }}
      >
        <div className="flex items-center gap-[0.85rem]">
          <Image
            src={phoneProfileAvatar}
            alt={`${PHONE_PROFILE.nickname} 프로필 이미지`}
            className="size-[4.3rem] shrink-0 rounded-full"
            priority
          />
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <h2 className="phone-home-nickname-sb truncate text-white">
                {PHONE_PROFILE.nickname}
              </h2>
              <button
                type="button"
                aria-label="프로필 수정"
                className="flex size-8 shrink-0 items-center justify-center text-white transition-transform hover:scale-105 focus-visible:outline-none"
              >
                <Image
                  src={phoneIconEdit}
                  alt=""
                  aria-hidden
                  className="size-6 object-contain"
                />
              </button>
            </div>
          </div>
        </div>
      </section>

      <div className="flex-1 overflow-y-auto bg-white pt-14">
        <div className="mx-auto grid w-[16.25rem] grid-cols-2 gap-x-8 gap-y-[2.35rem]">
          {PHONE_APP_SHORTCUTS.map(({
            key,
            label,
            asset,
            externalUrl,
            isEnabled,
          }) => {
            const handleClick =
              key === 'drawing'
                ? onOpenDrawing
                : key === 'gallery'
                  ? onOpenGallery
                  : undefined

            const shortcutContent = (
              <>
                <Image
                  src={asset}
                  alt=""
                  aria-hidden
                  className="size-[5.875rem] object-contain transition duration-200 group-hover:scale-105"
                />
                <span className="phone-home-app-label-m text-center text-fg-primary">
                  {label}
                </span>
              </>
            )

            if (externalUrl) {
              return (
                <Link
                  key={key}
                  href={externalUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="group flex min-h-[7.45rem] w-[7.15rem] flex-col items-center justify-start gap-2 transition duration-200 hover:-translate-y-1 focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-primary-2"
                >
                  {shortcutContent}
                </Link>
              )
            }

            return (
              <button
                key={key}
                type="button"
                disabled={!isEnabled}
                onClick={handleClick}
                className={cn(
                  'group flex min-h-[7.45rem] w-[7.15rem] flex-col items-center justify-start gap-2 transition duration-200',
                  isEnabled
                    ? 'hover:-translate-y-1 focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-primary-2'
                    : 'cursor-default',
                )}
              >
                {shortcutContent}
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}
