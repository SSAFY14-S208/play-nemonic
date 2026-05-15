'use client'

import { Clock3 } from 'lucide-react'

import { cn } from '@/shared/libs'

import type { GameLobbyTheme } from './GameLobbyLayout.types'

interface LobbyTimeLimitSelectorProps {
  theme: GameLobbyTheme
  timeLimitSeconds: number
  timeLimitAllowedSeconds: number[]
  isHost: boolean
  onChangeTimeLimit: (seconds: number) => void
  settingsError?: string | null
  variant: 'mobile' | 'desktop'
}

export function LobbyTimeLimitSelector({
  theme,
  timeLimitSeconds,
  timeLimitAllowedSeconds,
  isHost,
  onChangeTimeLimit,
  settingsError,
  variant,
}: LobbyTimeLimitSelectorProps) {
  if (timeLimitAllowedSeconds.length === 0) return null

  const isDesktop = variant === 'desktop'

  // 데스크탑에서는 카드로 감싸고, 모바일에서는 독립 섹션
  const content = (
    <>
      <h3
        className="h3-b inline-flex items-center gap-2"
        style={{ color: theme.ink }}
      >
        <Clock3
          className="size-5"
          strokeWidth={isDesktop ? 2.2 : undefined}
          style={{ color: theme.accent }}
          aria-hidden
        />
        제한 시간
      </h3>
      <div className="mt-4 grid grid-cols-3 gap-2">
        {timeLimitAllowedSeconds.map((seconds) => {
          const isSelected = seconds === timeLimitSeconds
          return (
            <button
              key={seconds}
              type="button"
              onClick={() => onChangeTimeLimit(seconds)}
              disabled={!isHost}
              className={cn(
                'body-b min-h-12 cursor-pointer rounded-[14px] border transition-all hover:-translate-y-0.5 hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0 disabled:hover:brightness-100',
                isDesktop && 'rounded-xl shadow-[0_7px_14px_rgba(0,0,0,0.08)]',
              )}
              style={
                isSelected
                  ? {
                      borderColor: theme.accent,
                      backgroundColor: theme.accent,
                      color: theme.ink,
                    }
                  : {
                      borderColor: theme.line,
                      backgroundColor: theme.active ?? theme.paper,
                      color: isDesktop ? theme.muted : theme.accent,
                    }
              }
            >
              {seconds}초
            </button>
          )
        })}
      </div>
      {settingsError && (
        <p role="alert" className="caption-r mt-3 text-error">
          {settingsError}
        </p>
      )}
    </>
  )

  if (!isDesktop) {
    return (
      <section
        className="rounded-[24px] border p-4 shadow-[0_12px_28px_rgba(0,0,0,0.08)] backdrop-blur-sm"
        style={{
          borderColor: theme.line,
          backgroundColor: theme.paperAlpha ?? theme.paper,
        }}
      >
        {content}
      </section>
    )
  }

  // 데스크탑 — 외부 카드 없이 내용만 반환 (부모가 카드 감쌈)
  return <>{content}</>
}
