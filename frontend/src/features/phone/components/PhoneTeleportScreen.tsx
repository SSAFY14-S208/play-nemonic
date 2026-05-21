'use client'

import { ArrowLeft, Brush, Home, Images, Printer, ScrollText, Sparkles } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { usePathname, useRouter } from 'next/navigation'
import { cn } from '@/shared/libs'
import {
  PHONE_COLORS,
  PHONE_TELEPORT_DESTINATION_GROUPS,
  type PhoneTeleportDestinationKey,
} from '../constants'
import { usePhoneStore } from '../phoneStore'

const TELEPORT_DESTINATION_ICONS: Record<PhoneTeleportDestinationKey, LucideIcon> = {
  community: Images,
  flipbook: Sparkles,
  fortune: ScrollText,
  hub: Home,
  infinite: Brush,
  nemonic: Printer,
  relay: Brush,
}

function isCurrentDestination(pathname: string, route: string) {
  if (route === '/') return pathname === route

  return pathname === route || pathname.startsWith(`${route}/`)
}

export function PhoneTeleportScreen() {
  const router = useRouter()
  const pathname = usePathname()
  const closePhone = usePhoneStore((state) => state.closePhone)
  const goHome = usePhoneStore((state) => state.goHome)

  const handleTeleport = (route: string) => {
    closePhone()
    if (isCurrentDestination(pathname, route)) return

    router.push(route)
  }

  return (
    <div className="flex h-full flex-col overflow-hidden bg-[#f6fbff] pt-8 text-fg-primary">
      <header
        className="shrink-0 border-b border-[#d7ebfb] px-3 pb-2"
        style={{ background: PHONE_COLORS.white }}
      >
        <div className="flex h-9 items-center justify-between">
          <button
            type="button"
            aria-label="홈으로 돌아가기"
            onClick={goHome}
            className="grid size-8 place-items-center rounded-full text-fg-secondary transition hover:bg-surface-subtle focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2"
          >
            <ArrowLeft className="size-4" />
          </button>
          <h2 className="phone-h3-b text-fg-primary">순간이동</h2>
          <span className="size-8" aria-hidden />
        </div>
      </header>

      <main className="no-scrollbar min-h-0 flex-1 overflow-y-auto px-3 py-3">
        <section className="mb-3 rounded-[0.7rem] border border-[#c9e9ff] bg-white p-3 shadow-[0_0.35rem_1rem_rgba(85,173,240,0.12)]">
          <div className="flex items-center gap-2">
            <span
              aria-hidden
              className="grid size-9 shrink-0 place-items-center rounded-[0.75rem] text-white"
              style={{ background: PHONE_COLORS.homeHeader }}
            >
              <Sparkles className="size-4.5" strokeWidth={2.45} />
            </span>
            <div className="min-w-0">
              <p className="phone-h4-b text-fg-primary">원하는 곳으로 바로 가요</p>
              <p className="phone-caption-r mt-0.5 text-fg-secondary">
                버튼을 누르면 폰이 닫히고 즉시 이동해요
              </p>
            </div>
          </div>
        </section>

        <div className="space-y-3">
          {PHONE_TELEPORT_DESTINATION_GROUPS.map((group) => (
            <section key={group.key}>
              <h3 className="phone-caption-b mb-1.5 px-1 text-fg-secondary">
                {group.label}
              </h3>
              <div className="grid grid-cols-2 gap-2">
                {group.destinations.map((destination) => {
                  const Icon = TELEPORT_DESTINATION_ICONS[destination.key]
                  const isCurrent = isCurrentDestination(pathname, destination.route)

                  return (
                    <button
                      key={destination.key}
                      type="button"
                      onClick={() => handleTeleport(destination.route)}
                      aria-current={isCurrent ? 'page' : undefined}
                      className={cn(
                        'group min-h-[5.25rem] rounded-[0.65rem] border bg-white p-2 text-left shadow-[0_0.14rem_0.5rem_rgba(17,21,29,0.07)] transition duration-200 focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2',
                        isCurrent
                          ? 'border-fg-primary'
                          : 'border-border-default hover:-translate-y-0.5',
                      )}
                    >
                      <span
                        aria-hidden
                        className="mb-1.5 grid size-8 place-items-center rounded-[0.6rem] transition duration-200 group-hover:scale-105"
                        style={{
                          background: destination.backgroundColor,
                          color: destination.accentColor,
                        }}
                      >
                        <Icon className="size-4" strokeWidth={2.45} />
                      </span>
                      <span className="phone-caption-b block truncate text-fg-primary">
                        {destination.label}
                      </span>
                      <span className="phone-caption-r mt-0.5 line-clamp-2 block text-fg-secondary">
                        {isCurrent ? '현재 위치' : destination.description}
                      </span>
                    </button>
                  )
                })}
              </div>
            </section>
          ))}
        </div>
      </main>
    </div>
  )
}
