import type { CSSProperties } from 'react'
import { toast, type ExternalToast } from 'sonner'

import type { GameLobbyTheme } from './GameLobbyLayout.types'

function createStyle(theme: GameLobbyTheme): CSSProperties {
  return {
    background: theme.paper,
    color: theme.ink,
    border: `1px solid ${theme.accent}`,
    fontFamily: 'var(--font-paperlogy)',
    fontWeight: 700,
    fontSize: '14px',
    lineHeight: '20px',
    borderRadius: '12px',
  }
}

function mergeOptions(
  theme: GameLobbyTheme,
  options?: ExternalToast,
): ExternalToast {
  return {
    ...options,
    style: { ...createStyle(theme), ...options?.style },
  }
}

/**
 * GameLobbyTheme 기반 toast wrapper factory.
 *
 * 릴레이 드로잉의 `relayToast`와 동일한 패턴이지만, 테마 객체를 받아
 * 어떤 게임 로비에서든 일관된 스타일의 토스트를 띄울 수 있다.
 *
 * 글로벌 `<Toaster>` (app/layout.tsx)가 이미 마운트돼 있어야 한다.
 */
export function createLobbyToast(theme: GameLobbyTheme) {
  const lobbyToast = (message: string, options?: ExternalToast) =>
    toast(message, mergeOptions(theme, options))

  lobbyToast.success = (message: string, options?: ExternalToast) =>
    toast.success(message, mergeOptions(theme, options))

  lobbyToast.error = (message: string, options?: ExternalToast) =>
    toast.error(message, mergeOptions(theme, options))

  return lobbyToast
}
