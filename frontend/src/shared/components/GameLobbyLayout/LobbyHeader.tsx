"use client";

import { ArrowLeft, HelpCircle } from "lucide-react";

import type { GameLobbyTheme } from "./GameLobbyLayout.types";

interface LobbyHeaderProps {
  theme: GameLobbyTheme;
  onLeave: () => void;
  hasHowToPlay: boolean;
  onOpenHowToPlay: () => void;
  /** 모바일/데스크탑 간 약간의 사이즈 차이를 위한 variant */
  variant: "mobile" | "desktop";
}

export function LobbyHeader({
  theme,
  onLeave,
  hasHowToPlay,
  onOpenHowToPlay,
  variant,
}: LobbyHeaderProps) {
  const isMobile = variant === "mobile";

  return (
    <header className="flex w-full items-center justify-between">
      <button
        type="button"
        onClick={onLeave}
        className="body-b inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-3.5 py-1.5 shadow-sm transition-all hover:-translate-y-0.5 hover:brightness-95"
        style={{
          borderColor: theme.line,
          backgroundColor: theme.paper,
          color: theme.ink,
          ...(isMobile
            ? {}
            : { paddingInline: "1rem", paddingBlock: "0.5rem" }),
        }}
      >
        <ArrowLeft className="size-5" aria-hidden />
        나가기
      </button>

      {hasHowToPlay && (
        <button
          type="button"
          onClick={onOpenHowToPlay}
          aria-label="게임 설명"
          className="grid cursor-pointer place-items-center rounded-full border shadow-sm transition-all hover:-translate-y-0.5 hover:brightness-95"
          style={{
            width: isMobile ? 36 : 40,
            height: isMobile ? 36 : 40,
            borderColor: theme.line,
            backgroundColor: theme.paper,
            color: theme.ink,
          }}
        >
          <HelpCircle
            className="size-5"
            style={{ color: theme.accentStrong ?? theme.accent }}
            aria-hidden
          />
        </button>
      )}
    </header>
  );
}
