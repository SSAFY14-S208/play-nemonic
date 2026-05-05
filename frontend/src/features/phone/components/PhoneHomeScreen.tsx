import { Edit3, Sparkles } from 'lucide-react'
import { cn } from '@/shared/libs'
import {
  PHONE_APP_SHORTCUTS,
  PHONE_GALLERY_ITEM_STYLES,
  PHONE_PROFILE,
} from '../constants'
import type { PhoneGalleryItem } from '../types'

interface PhoneHomeScreenProps {
  recentGalleryItems: PhoneGalleryItem[]
  onOpenDrawing: () => void
  onOpenGallery: () => void
}

export function PhoneHomeScreen({
  recentGalleryItems,
  onOpenDrawing,
  onOpenGallery,
}: PhoneHomeScreenProps) {
  return (
    <div className="flex h-full flex-col bg-surface-default">
      <section className="bg-[#55adf0] px-10 pb-10 pt-20 text-white">
        <div className="flex items-center gap-6">
          <div className="relative flex h-24 w-24 shrink-0 items-center justify-center rounded-full border-[0.35rem] border-white bg-[#f8fbff] shadow-[0_0.5rem_1.25rem_rgba(36,101,158,0.18)]">
            <div className="absolute bottom-4 h-9 w-16 rounded-t-full bg-[#a8d8ff]" />
            <div className="relative flex h-14 w-14 items-center justify-center rounded-full bg-[#fff3e8] text-xl shadow-[inset_0_-0.35rem_0_rgba(255,205,177,0.5)]">
              <span className="absolute left-3 top-6 h-1.5 w-1.5 rounded-full bg-[#241f1d]" />
              <span className="absolute right-3 top-6 h-1.5 w-1.5 rounded-full bg-[#241f1d]" />
              <span className="absolute bottom-4 h-1.5 w-5 rounded-b-full border-b-2 border-[#dc6f78]" />
            </div>
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-3">
              <h2 className="h1-b truncate text-white">
                {PHONE_PROFILE.nickname}
              </h2>
              <button
                type="button"
                aria-label="프로필 수정"
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-white transition-transform hover:scale-105 focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-white"
              >
                <Edit3 className="h-7 w-7" />
              </button>
            </div>
          </div>
        </div>
      </section>

      <div className="flex-1 overflow-y-auto px-8 py-8">
        <div className="grid grid-cols-2 gap-x-10 gap-y-9">
          {PHONE_APP_SHORTCUTS.map(({ key, label, Icon, background, accent, isEnabled }) => {
            const handleClick =
              key === 'drawing'
                ? onOpenDrawing
                : key === 'gallery'
                  ? onOpenGallery
                  : undefined

            return (
              <button
                key={key}
                type="button"
                disabled={!isEnabled}
                onClick={handleClick}
                className={cn(
                  'group flex min-h-[9.25rem] flex-col items-center justify-start gap-3 rounded-[var(--radius-xl)] p-2 transition duration-200',
                  isEnabled
                    ? 'hover:-translate-y-1 focus-visible:outline focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-primary-2'
                    : 'cursor-default',
                )}
              >
                <span
                  className="flex h-28 w-28 items-center justify-center rounded-[2.4rem] shadow-[0_0.8rem_1.6rem_rgba(53,57,72,0.12)] transition duration-200 group-hover:scale-105"
                  style={{ background }}
                >
                  <Icon
                    className="h-14 w-14"
                    color={key === 'shop' || key === 'settings' ? 'rgba(255,255,255,0.92)' : accent}
                    strokeWidth={2.2}
                  />
                </span>
                <span className="h3-b text-center text-fg-primary">
                  {label}
                </span>
              </button>
            )
          })}
        </div>

        <section className="mt-8 rounded-[1.4rem] border border-border-default bg-white p-5 shadow-[0_0.75rem_2rem_rgba(78,86,105,0.08)]">
          <div className="mb-4 flex items-center justify-between gap-4">
            <div>
              <p className="caption-b text-primary-2">최근 카드</p>
              <h3 className="h4-b text-fg-primary">방금 저장한 조각들</h3>
            </div>
            <Sparkles className="h-5 w-5 text-primary-2" />
          </div>
          <div className="grid grid-cols-3 gap-2">
            {recentGalleryItems.slice(0, 3).map((item) => {
              const itemStyle = PHONE_GALLERY_ITEM_STYLES[item.kind]

              return (
                <div
                  key={item.id}
                  className="min-h-20 rounded-[var(--radius-lg)] p-2"
                  style={{ background: itemStyle.background }}
                >
                  <span
                    className="caption-b inline-flex rounded-full px-2 py-0.5"
                    style={{ color: itemStyle.color, background: 'rgba(255,255,255,0.72)' }}
                  >
                    {itemStyle.label}
                  </span>
                  <p className="caption-b mt-2 line-clamp-2 text-fg-primary">
                    {item.title}
                  </p>
                </div>
              )
            })}
          </div>
        </section>
      </div>
    </div>
  )
}
