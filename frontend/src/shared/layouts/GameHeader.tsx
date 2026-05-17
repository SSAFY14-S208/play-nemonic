'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Box, CircleUserRound } from 'lucide-react'
import { cn } from '@/shared/libs'

type GameNavigationKey = 'square' | 'fortune' | 'infinite' | 'flipbook' | 'relay' | 'community'

interface GameNavigationItem {
  key: GameNavigationKey
  label: string
  href: string
}

const GAME_NAVIGATION_ITEMS: GameNavigationItem[] = [
  { key: 'square', label: '광장', href: '/hub' },
  { key: 'fortune', label: '운세', href: '/hub' },
  { key: 'infinite', label: '무한', href: '/infinite-canvas' },
  { key: 'flipbook', label: '플립북', href: '/flipbook' },
  { key: 'relay', label: '릴레이', href: '/relay-drawing' },
  { key: 'community', label: '커뮤니티', href: '/hub' },
]

function getActiveNavigationKey(pathname: string): GameNavigationKey | null {
  if (pathname.startsWith('/relay-drawing')) return 'relay'
  if (pathname.startsWith('/flipbook')) return 'flipbook'
  if (pathname.startsWith('/infinite-canvas')) return 'infinite'

  return null
}

export default function GameHeader() {
  const pathname = usePathname()
  const activeNavigationKey = getActiveNavigationKey(pathname)

  return (
    <header className="sticky top-0 z-[var(--z-sticky)] border-b border-relay-border bg-relay-header/95 backdrop-blur-md">
      <div className="mx-auto flex h-[72px] w-full max-w-[1440px] items-center justify-between px-6">
        <Link href="/hub" className="flex items-center gap-3 text-relay-ink">
          <span className="grid size-8 place-items-center rounded-[var(--radius-md)] bg-relay-blue text-fg-inverse shadow-sm">
            N
          </span>
          <span className="body-b">Play! Nemonic</span>
        </Link>

        <nav className="hidden items-center gap-5 md:flex">
          {GAME_NAVIGATION_ITEMS.map((item) => {
            const isActive = item.key === activeNavigationKey

            return (
              <Link
                key={item.key}
                href={item.href}
                className={cn(
                  'caption-m rounded-full px-4 py-2 text-relay-muted transition-colors',
                  isActive && 'bg-relay-active text-relay-blue',
                )}
              >
                {item.label}
              </Link>
            )
          })}
        </nav>

        <div className="flex items-center gap-4">
          <span className="hidden items-center gap-1 text-relay-ink sm:flex">
            <Box className="size-4 text-relay-muted" aria-hidden />
            <span className="caption-b">보관함 3</span>
          </span>
          <button
            type="button"
            aria-label="내 프로필"
            className="grid size-9 place-items-center rounded-full bg-relay-blue text-fg-inverse shadow-sm"
          >
            <CircleUserRound className="size-5" aria-hidden />
          </button>
        </div>
      </div>
    </header>
  )
}
