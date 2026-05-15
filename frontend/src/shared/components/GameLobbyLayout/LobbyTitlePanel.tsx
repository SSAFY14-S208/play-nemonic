'use client'

import Image from 'next/image'
import type { StaticImageData } from 'next/image'

import type { GameLobbyTheme } from './GameLobbyLayout.types'
import { LobbyShareButtons } from './LobbyShareButtons'

interface LobbyTitlePanelProps {
  theme: GameLobbyTheme
  titleImage: StaticImageData | string
  titleImageAlt: string
  subtitle?: string
  roomCode: string
  variant: 'mobile' | 'desktop'
}

export function LobbyTitlePanel({
  theme,
  titleImage,
  titleImageAlt,
  subtitle,
  roomCode,
  variant,
}: LobbyTitlePanelProps) {
  const isMobile = variant === 'mobile'

  if (isMobile) {
    return (
      <section
        className="rounded-[28px] border p-5 text-center shadow-[0_14px_32px_rgba(0,0,0,0.1)] backdrop-blur-sm"
        style={{
          borderColor: theme.line,
          backgroundColor: theme.paperAlpha ?? theme.paper,
          color: theme.ink,
        }}
      >
        <Image
          src={titleImage}
          alt={titleImageAlt}
          width={240}
          height={60}
          className="mx-auto h-auto w-45"
        />
        {subtitle && (
          <p className="body-b mt-2" style={{ color: theme.muted }}>
            {subtitle}
          </p>
        )}
        <div
          className="mt-5 rounded-[22px] border px-4 py-6"
          style={{
            borderColor: theme.line,
            backgroundColor: theme.paperAlpha ?? theme.paper,
          }}
        >
          <p className="body-b" style={{ color: theme.ink }}>
            입장 코드
          </p>
          <p
            className="mt-3 break-all font-black leading-none tracking-[0.04em]"
            style={{ fontSize: 'clamp(2rem, 10vw, 3.5rem)', color: theme.ink }}
          >
            {roomCode}
          </p>
          <LobbyShareButtons theme={theme} roomCode={roomCode} />
        </div>
      </section>
    )
  }

  // ── 데스크탑 ──
  return (
    <div className="w-full max-w-100 text-center">
      <Image
        src={titleImage}
        alt={titleImageAlt}
        width={480}
        height={120}
        className="mx-auto h-auto w-full max-w-70"
      />
      {subtitle && (
        <div
          className="mx-auto mt-5 inline-flex min-h-10 items-center rounded-lg px-7 shadow-[inset_0_-6px_0_rgb(255_255_255_/_24%)]"
          style={{ backgroundColor: `color-mix(in srgb, ${theme.accent} 30%, transparent)`, color: theme.ink }}
        >
          <span className="body-b">{subtitle}</span>
        </div>
      )}

      <section
        className="mt-7 w-full max-w-[420px] rounded-3xl border px-8 py-8 text-center shadow-[0_16px_34px_rgba(0,0,0,0.1)] backdrop-blur-[1px]"
        style={{
          borderColor: theme.line,
          backgroundColor: theme.paperAlpha ?? theme.paper,
          color: theme.ink,
        }}
      >
        <p className="h3-b" style={{ color: theme.ink }}>
          입장 코드
        </p>
        <p
          className="mt-3 break-all font-black leading-none"
          style={{
            fontSize: 'clamp(2rem, 4vw, 3.5rem)',
            letterSpacing: '0.04em',
            color: theme.ink,
          }}
        >
          {roomCode}
        </p>
        <div
          aria-hidden
          className="mx-auto mt-4 h-2 w-[min(72%,224px)] rounded-full"
          style={{
            background: `repeating-linear-gradient(90deg, ${theme.accent} 0 12px, transparent 12px 18px)`,
          }}
        />
        <p className="body-b mt-5" style={{ color: theme.muted }}>
          친구에게 코드를 알려주세요!
        </p>
        <LobbyShareButtons theme={theme} roomCode={roomCode} />
      </section>
    </div>
  )
}
