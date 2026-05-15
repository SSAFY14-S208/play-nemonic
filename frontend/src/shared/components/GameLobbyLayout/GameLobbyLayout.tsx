"use client";

import { useState } from "react";
import Image from "next/image";
import { motion } from "motion/react";

import { HowToPlayModal } from "../HowToPlayModal";

import type { GameLobbyLayoutProps } from "./GameLobbyLayout.types";
import { LobbyHeader } from "./LobbyHeader";
import { LobbyParticipantSection } from "./LobbyParticipantSection";
import { LobbyTimeLimitSelector } from "./LobbyTimeLimitSelector";
import { LobbyTitlePanel } from "./LobbyTitlePanel";

const EXIT_EASE: [number, number, number, number] = [0.65, 0, 0.35, 1];

export function GameLobbyLayout({
  theme,
  titleImage,
  titleImageAlt,
  subtitle,
  roomCode,
  participants,
  maxParticipants,
  minParticipants,
  currentUserUuid,
  participantListMaxHeight,
  waitingSlotText,
  kickingTargetUuid,
  kickError,
  onKickParticipant,
  isHost,
  timeLimitSeconds,
  timeLimitAllowedSeconds,
  onChangeTimeLimit,
  settingsError,
  canStartGame,
  isStarting,
  startButtonLabel,
  onStartGame,
  nonHostMessage = "방장이 게임을 시작할 때까지 기다려주세요",
  onLeave,
  isExiting = false,
  backgroundImage,
  backgroundOverlay,
  howToPlayPanels,
  howToPlayAccentColor,
}: GameLobbyLayoutProps) {
  const [isHowToPlayModalOpen, setIsHowToPlayModalOpen] = useState(false);

  const hasHowToPlay = !!howToPlayPanels && howToPlayPanels.length > 0;
  const hasTimeLimit =
    timeLimitSeconds !== undefined &&
    timeLimitAllowedSeconds !== undefined &&
    timeLimitAllowedSeconds.length > 0 &&
    onChangeTimeLimit !== undefined;

  return (
    <section
      className="relative min-h-screen overflow-x-hidden overflow-y-auto"
      style={{ color: theme.ink }}
    >
      {backgroundImage && (
        <Image
          src={backgroundImage}
          alt=""
          fill
          priority
          sizes="100vw"
          className="pointer-events-none object-cover"
          aria-hidden
        />
      )}
      {backgroundOverlay && (
        <div
          className="absolute inset-0"
          style={{ background: backgroundOverlay }}
          aria-hidden
        />
      )}

      {/* ─── 모바일 레이아웃 (lg 미만) ─── */}
      <motion.div
        className="relative z-10 grid w-full gap-4 px-4 pb-8 pt-6 lg:hidden"
        animate={isExiting ? { opacity: 0, y: 40 } : { opacity: 1, y: 0 }}
        transition={{ duration: 0.45, ease: EXIT_EASE }}
      >
        <LobbyHeader
          theme={theme}
          onLeave={onLeave}
          hasHowToPlay={hasHowToPlay}
          onOpenHowToPlay={() => setIsHowToPlayModalOpen(true)}
          variant="mobile"
        />

        <LobbyTitlePanel
          theme={theme}
          titleImage={titleImage}
          titleImageAlt={titleImageAlt}
          subtitle={subtitle}
          roomCode={roomCode}
          variant="mobile"
        />

        <section
          className="rounded-[24px] border p-4 shadow-[0_12px_28px_rgba(0,0,0,0.08)] backdrop-blur-sm"
          style={{
            borderColor: theme.line,
            backgroundColor: theme.paperAlpha ?? theme.paper,
          }}
        >
          <LobbyParticipantSection
            theme={theme}
            participants={participants}
            maxParticipants={maxParticipants}
            minParticipants={minParticipants}
            currentUserUuid={currentUserUuid}
            participantListMaxHeight={participantListMaxHeight}
            waitingSlotText={waitingSlotText}
            isHost={isHost}
            kickingTargetUuid={kickingTargetUuid}
            kickError={kickError}
            onKickParticipant={onKickParticipant}
            variant="mobile"
          />
        </section>

        {hasTimeLimit && (
          <LobbyTimeLimitSelector
            theme={theme}
            timeLimitSeconds={timeLimitSeconds}
            timeLimitAllowedSeconds={timeLimitAllowedSeconds}
            isHost={isHost}
            onChangeTimeLimit={onChangeTimeLimit}
            settingsError={settingsError}
            variant="mobile"
          />
        )}

        <MobileStartButton
          theme={theme}
          isHost={isHost}
          canStartGame={canStartGame}
          isStarting={isStarting}
          startButtonLabel={startButtonLabel}
          onStartGame={onStartGame}
          nonHostMessage={nonHostMessage}
        />
      </motion.div>

      {/* ─── 데스크탑 레이아웃 (lg+) ─── */}
      <motion.div className="relative z-10 hidden min-h-screen w-full flex-col lg:flex">
        <motion.div
          className="mx-auto flex w-full max-w-300 px-8 pt-6"
          animate={isExiting ? { opacity: 0, y: -20 } : { opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
        >
          <LobbyHeader
            theme={theme}
            onLeave={onLeave}
            hasHowToPlay={hasHowToPlay}
            onOpenHowToPlay={() => setIsHowToPlayModalOpen(true)}
            variant="desktop"
          />
        </motion.div>

        <main className="mx-auto grid w-full max-w-300 flex-1 items-center gap-10 px-8 pb-8 lg:grid-cols-[2fr_3fr] xl:gap-14">
          {/* 좌측: 타이틀 + 입장 코드 + 공유 */}
          <motion.aside
            className="flex w-full flex-col items-center"
            animate={
              isExiting ? { x: "-100%", opacity: 0 } : { x: 0, opacity: 1 }
            }
            transition={{ duration: 0.5, ease: EXIT_EASE }}
          >
            <LobbyTitlePanel
              theme={theme}
              titleImage={titleImage}
              titleImageAlt={titleImageAlt}
              subtitle={subtitle}
              roomCode={roomCode}
              variant="desktop"
            />
          </motion.aside>

          {/* 우측: 참여자 + 제한 시간 + 참여 조건 + 시작 */}
          <motion.section
            className="rounded-[32px] border p-8 shadow-[0_18px_44px_rgba(0,0,0,0.1)] backdrop-blur-sm"
            style={{
              borderColor: theme.line,
              backgroundColor: "rgba(255, 255, 255, 0.68)",
            }}
            animate={
              isExiting ? { x: "100%", opacity: 0 } : { x: 0, opacity: 1 }
            }
            transition={{ duration: 0.5, ease: EXIT_EASE }}
          >
            <LobbyParticipantSection
              theme={theme}
              participants={participants}
              maxParticipants={maxParticipants}
              minParticipants={minParticipants}
              currentUserUuid={currentUserUuid}
              participantListMaxHeight={participantListMaxHeight}
              waitingSlotText={waitingSlotText}
              isHost={isHost}
              kickingTargetUuid={kickingTargetUuid}
              kickError={kickError}
              onKickParticipant={onKickParticipant}
              variant="desktop"
            />

            {/* 제한 시간 + 참여 조건 */}
            <div className="mt-6 grid gap-4 xl:grid-cols-2">
              {hasTimeLimit && (
                <section
                  className="rounded-2xl border p-5 shadow-[inset_0_1px_0_rgb(255_255_255_/_82%)]"
                  style={{
                    borderColor: theme.line,
                    backgroundColor: "rgba(255, 255, 255, 0.54)",
                  }}
                >
                  <LobbyTimeLimitSelector
                    theme={theme}
                    timeLimitSeconds={timeLimitSeconds}
                    timeLimitAllowedSeconds={timeLimitAllowedSeconds}
                    isHost={isHost}
                    onChangeTimeLimit={onChangeTimeLimit}
                    settingsError={settingsError}
                    variant="desktop"
                  />
                </section>
              )}

              <section
                className="rounded-2xl border p-5 shadow-[inset_0_1px_0_rgb(255_255_255_/_82%)]"
                style={{
                  borderColor: theme.line,
                  backgroundColor: "rgba(255, 255, 255, 0.54)",
                }}
              >
                <h3 className="h3-b" style={{ color: theme.ink }}>
                  참여 조건
                </h3>
                <div className="flex h-full items-center">
                  <p className="text-l mb-1" style={{ color: theme.muted }}>
                    현재 {participants.length}명 참여 중 · 최소{" "}
                    {minParticipants}명 필요
                  </p>
                </div>
              </section>
            </div>

            {/* 시작 버튼 */}
            <div className="mt-6 flex flex-col gap-2">
              {isHost ? (
                <button
                  type="button"
                  onClick={onStartGame}
                  disabled={!canStartGame || isStarting}
                  className="body-b min-h-14 w-full cursor-pointer rounded-2xl px-7 shadow-[0_8px_20px_rgba(0,0,0,0.2)] transition-all hover:-translate-y-0.5 hover:brightness-105 disabled:opacity-45 disabled:hover:translate-y-0 disabled:hover:brightness-100"
                  style={{
                    backgroundColor: theme.accent,
                    color: theme.ink,
                  }}
                >
                  {startButtonLabel}
                </button>
              ) : (
                <div
                  className="body-r grid min-h-14 place-items-center rounded-2xl border border-dashed px-5"
                  style={{
                    borderColor: theme.accent,
                    color: theme.muted,
                  }}
                >
                  {nonHostMessage}
                </div>
              )}
            </div>
          </motion.section>
        </main>
      </motion.div>

      {hasHowToPlay && (
        <HowToPlayModal
          open={isHowToPlayModalOpen}
          onOpenChange={setIsHowToPlayModalOpen}
          panels={howToPlayPanels}
          accentColor={howToPlayAccentColor}
        />
      )}
    </section>
  );
}

// ── 모바일 시작 버튼 ──

interface MobileStartButtonProps {
  theme: GameLobbyLayoutProps["theme"];
  isHost: boolean;
  canStartGame: boolean;
  isStarting: boolean;
  startButtonLabel: string;
  onStartGame: () => void;
  nonHostMessage: string;
}

function MobileStartButton({
  theme,
  isHost,
  canStartGame,
  isStarting,
  startButtonLabel,
  onStartGame,
  nonHostMessage,
}: MobileStartButtonProps) {
  return (
    <div className="flex flex-col gap-2">
      {isHost ? (
        <button
          type="button"
          onClick={onStartGame}
          disabled={!canStartGame || isStarting}
          className="body-b min-h-14 w-full cursor-pointer rounded-2xl px-7 shadow-[0_6px_16px_rgba(0,0,0,0.25)] transition-all hover:-translate-y-0.5 hover:brightness-105 disabled:opacity-45 disabled:hover:translate-y-0 disabled:hover:brightness-100"
          style={{
            backgroundColor: theme.accent,
            color: theme.ink,
          }}
        >
          {startButtonLabel}
        </button>
      ) : (
        <div
          className="body-r grid min-h-13 place-items-center rounded-2xl border border-dashed px-5"
          style={{
            borderColor: theme.accent,
            color: theme.muted,
          }}
        >
          {nonHostMessage}
        </div>
      )}
    </div>
  );
}
