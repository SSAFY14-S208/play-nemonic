"use client";

import Image from "next/image";
import { ArrowLeft, Clock3, Crown, UsersRound, X } from "lucide-react";
import { motion } from "motion/react";

import { cn } from "@/shared/libs";
import { useUserStore } from "@/shared/stores";
import type { RelayRoomParticipantResponse } from "@/shared/types";

import relayDrawingTitle from "../assets/relay-drawing-title.png";
import {
  RELAY_QR_ACTION,
  RELAY_ROOM_CODE,
  RELAY_SHARE_ACTIONS,
} from "../constants";
import { useRelayLobby } from "../hooks";
import { useRelayDrawingStore } from "../stores";

import RelayButton from "./RelayButton";
import RelayLobbyShareButton from "./RelayLobbyShareButton";

export default function RelayLobbyView() {
  const roomCode = useRelayDrawingStore((state) => state.roomCode);
  const participants = useRelayDrawingStore((state) => state.participants);
  const maxParticipants = useRelayDrawingStore(
    (state) => state.maxParticipants,
  );
  const minParticipants = useRelayDrawingStore(
    (state) => state.minParticipants,
  );
  const timeLimitSeconds = useRelayDrawingStore(
    (state) => state.timeLimitSeconds,
  );
  const timeLimitAllowedSeconds = useRelayDrawingStore(
    (state) => state.timeLimitAllowedSeconds,
  );
  const currentUserUuid = useUserStore((state) => state.userUuid);

  const gameStartPhase = useRelayDrawingStore(
    (state) => state.gameStartPhase,
  );
  const isExiting = gameStartPhase === "animating";

  const {
    isHost,
    canStartGame,
    isStarting,
    settingsError,
    kickingTargetUuid,
    kickError,
    startGame,
    changeTimeLimit,
    kickParticipant,
    leaveRoom,
  } = useRelayLobby();

  const waitingSlotCount = Math.max(0, maxParticipants - participants.length);
  const startButtonLabel = isStarting
    ? "시작 중…"
    : `게임 시작 (${participants.length}명)`;

  return (
    <section className="relative min-h-screen overflow-x-hidden overflow-y-auto text-relay-ink">
      {/* ─── 모바일 레이아웃 (lg 미만) ─── */}
      <motion.div
        className="relative z-10 grid w-full gap-4 px-4 pb-8 pt-6 lg:hidden"
        animate={isExiting ? { opacity: 0, y: 40 } : { opacity: 1, y: 0 }}
        transition={{ duration: 0.45, ease: [0.65, 0, 0.35, 1] }}
      >
        {/* 나가기 */}
        <header className="flex items-center">
          <button
            type="button"
            onClick={leaveRoom}
            className="body-b inline-flex cursor-pointer items-center gap-1.5 rounded-full border border-relay-line bg-relay-paper px-3.5 py-1.5 text-relay-ink shadow-sm transition-all hover:-translate-y-0.5 hover:brightness-95"
          >
            <ArrowLeft className="size-5" aria-hidden />
            나가기
          </button>
        </header>

        {/* 타이틀 + 입장 코드 + 공유 */}
        <section className="rounded-[28px] border border-relay-line bg-relay-paper/78 p-5 text-center shadow-[0_14px_32px_rgba(184,121,22,0.14)] backdrop-blur-sm">
          <Image
            src={relayDrawingTitle}
            alt="네모닉 드로잉"
            width={240}
            height={60}
            className="mx-auto h-auto w-[180px]"
          />
          <p className="body-b mt-2 text-relay-muted">
            친구들이 모이면 바로 시작해요!
          </p>
          <div className="mt-5 rounded-[22px] border border-relay-line bg-relay-paper/90 px-4 py-6">
            <p className="body-b text-relay-ink">입장 코드</p>
            <p
              className="mt-3 break-all font-black leading-none tracking-[0.04em] text-relay-ink"
              style={{ fontSize: "clamp(2rem, 10vw, 3.5rem)" }}
            >
              {roomCode ?? RELAY_ROOM_CODE}
            </p>
            <div className="mt-5 grid grid-cols-2 gap-3">
              {RELAY_SHARE_ACTIONS.map((action) => (
                <RelayLobbyShareButton
                  key={action.key}
                  actionKey={action.key}
                  label={action.label}
                  Icon={action.Icon}
                  roomCode={roomCode}
                />
              ))}
            </div>
            <div className="mt-3">
              <RelayLobbyShareButton
                actionKey={RELAY_QR_ACTION.key}
                label={RELAY_QR_ACTION.label}
                Icon={RELAY_QR_ACTION.Icon}
                roomCode={roomCode}
                className="w-full"
              />
            </div>
          </div>
        </section>

        {/* 참여자 */}
        <section className="rounded-[24px] border border-relay-line bg-relay-paper/82 p-4 shadow-[0_12px_28px_rgba(184,121,22,0.12)] backdrop-blur-sm">
          <div className="flex items-center justify-between border-b border-relay-line pb-4">
            <h2 className="h3-b inline-flex items-center gap-2 text-relay-ink">
              <UsersRound className="size-5" aria-hidden />
              참여자
            </h2>
            <span className="h2-b text-relay-accent">
              {participants.length} / {maxParticipants}
            </span>
          </div>
          <p className="caption-b mt-3 text-relay-muted">
            최소 {minParticipants}명 필요
          </p>
          <div className="mt-4 grid gap-3">
            {participants.map((participant) => (
              <ParticipantTile
                key={participant.userUuid}
                participant={participant}
                isMe={participant.userUuid === currentUserUuid}
                canKick={isHost && participant.userUuid !== currentUserUuid}
                isKicking={kickingTargetUuid === participant.userUuid}
                onKick={() => kickParticipant(participant.userUuid)}
              />
            ))}
            {Array.from({ length: waitingSlotCount }).map(
              (_, waitingSlotIndex) => (
                <div
                  key={`waiting-${waitingSlotIndex}`}
                  className="caption-b grid min-h-11 place-items-center rounded-[14px] border border-dashed border-relay-accent text-relay-dash"
                >
                  초대를 기다리는 중...
                </div>
              ),
            )}
          </div>
          {kickError && (
            <p role="alert" className="caption-r mt-3 text-error">
              {kickError}
            </p>
          )}
        </section>

        {/* 제한 시간 */}
        <section className="rounded-[24px] border border-relay-line bg-relay-paper/82 p-4 shadow-[0_12px_28px_rgba(184,121,22,0.12)] backdrop-blur-sm">
          <h3 className="h3-b inline-flex items-center gap-2 text-relay-ink">
            <Clock3 className="size-5 text-relay-accent" aria-hidden />
            제한 시간
          </h3>
          <div className="mt-4 grid grid-cols-3 gap-2">
            {timeLimitAllowedSeconds.map((seconds) => {
              const isSelected = seconds === timeLimitSeconds;
              return (
                <button
                  key={seconds}
                  type="button"
                  onClick={() => changeTimeLimit(seconds)}
                  disabled={!isHost}
                  className={cn(
                    "body-b min-h-12 cursor-pointer rounded-[14px] border border-relay-line bg-relay-active text-relay-accent transition-all hover:-translate-y-0.5 hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0 disabled:hover:brightness-100",
                    isSelected &&
                      "border-relay-accent bg-relay-accent text-relay-ink",
                  )}
                >
                  {seconds}초
                </button>
              );
            })}
          </div>
          {settingsError && (
            <p role="alert" className="caption-r mt-3 text-error">
              {settingsError}
            </p>
          )}
        </section>

        {/* 시작 버튼 */}
        <div className="flex flex-col gap-2">
          {isHost ? (
            <RelayButton
              onClick={startGame}
              disabled={!canStartGame}
              size="lg"
              className="w-full rounded-2xl shadow-[0_6px_16px_rgba(184,121,22,0.4)]"
            >
              {startButtonLabel}
            </RelayButton>
          ) : (
            <div className="body-r grid min-h-13 place-items-center rounded-2xl border border-dashed border-relay-accent px-5 text-relay-muted">
              방장이 게임을 시작할 때까지 기다려주세요
            </div>
          )}
        </div>
      </motion.div>

      {/* ─── 데스크탑 레이아웃 (lg+) ─── */}
      <motion.div className="relative z-10 hidden w-full min-h-screen flex-col lg:flex">
        {/* 나가기 — 데스크탑 좌상단 */}
        <motion.header
          className="shrink-0 px-8 pt-6"
          animate={isExiting ? { opacity: 0, y: -20 } : { opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
        >
          <button
            type="button"
            onClick={leaveRoom}
            className="body-b inline-flex cursor-pointer items-center gap-1.5 rounded-full border border-relay-line bg-relay-paper px-4 py-2 text-relay-ink shadow-sm transition-all hover:-translate-y-0.5 hover:brightness-95"
          >
            <ArrowLeft className="size-5" aria-hidden />
            나가기
          </button>
        </motion.header>

        <main className="mx-auto grid w-full max-w-[1400px] flex-1 items-center gap-10 px-8 pb-8 lg:grid-cols-[2fr_3fr] xl:gap-14">
          {/* 좌측: 타이틀 + 입장 코드 + 공유 */}
          <motion.aside
            className="flex w-full flex-col items-center"
            animate={
              isExiting
                ? { x: "-100%", opacity: 0 }
                : { x: 0, opacity: 1 }
            }
            transition={{ duration: 0.5, ease: [0.65, 0, 0.35, 1] }}
          >
            <div className="w-full max-w-[400px] text-center">
              <Image
                src={relayDrawingTitle}
                alt="네모닉 드로잉"
                width={480}
                height={120}
                className="mx-auto h-auto w-full max-w-[280px]"
              />
              <div className="mx-auto mt-5 inline-flex min-h-10 items-center rounded-lg bg-relay-accent/30 px-7 text-relay-ink shadow-[inset_0_-6px_0_rgb(255_255_255_/_24%)]">
                <span className="body-b">친구들이 모이면 바로 시작해요!</span>
              </div>
            </div>

            <section className="mt-7 w-full max-w-[420px] rounded-3xl border border-relay-line bg-relay-paper/82 px-8 py-8 text-center shadow-[0_16px_34px_rgba(184,121,22,0.14),inset_0_0_34px_rgb(255_244_226_/_70%)] backdrop-blur-[1px]">
              <p className="h3-b text-relay-ink">입장 코드</p>
              <p
                className="mt-3 break-all font-black leading-none text-relay-ink"
                style={{
                  fontSize: "clamp(2rem, 4vw, 3.5rem)",
                  letterSpacing: "0.04em",
                }}
              >
                {roomCode ?? RELAY_ROOM_CODE}
              </p>
              <div
                aria-hidden
                className="mx-auto mt-4 h-2 w-[min(72%,224px)] rounded-full bg-[repeating-linear-gradient(90deg,var(--color-relay-accent)_0_12px,transparent_12px_18px)]"
              />
              <p className="body-b mt-5 text-relay-muted">
                친구에게 코드를 알려주세요!
              </p>

              <div className="mt-6 grid grid-cols-2 gap-3">
                {RELAY_SHARE_ACTIONS.map((action) => (
                  <RelayLobbyShareButton
                    key={action.key}
                    actionKey={action.key}
                    label={action.label}
                    Icon={action.Icon}
                    roomCode={roomCode}
                    className="min-h-[42px] rounded-xl"
                  />
                ))}
              </div>
              <div className="mt-3">
                <RelayLobbyShareButton
                  actionKey={RELAY_QR_ACTION.key}
                  label={RELAY_QR_ACTION.label}
                  Icon={RELAY_QR_ACTION.Icon}
                  roomCode={roomCode}
                  className="min-h-[42px] w-full rounded-xl"
                />
              </div>
            </section>
          </motion.aside>

          {/* 우측: 참여자 + 제한 시간 + 시작 */}
          <motion.section
            className="rounded-[32px] border border-relay-line bg-white/68 p-8 shadow-[0_18px_44px_rgba(184,121,22,0.14),inset_0_1px_0_rgb(255_255_255_/_86%)] backdrop-blur-sm"
            animate={
              isExiting
                ? { x: "100%", opacity: 0 }
                : { x: 0, opacity: 1 }
            }
            transition={{ duration: 0.5, ease: [0.65, 0, 0.35, 1] }}
          >
            <div className="flex items-center justify-between border-b border-relay-line pb-5">
              <h2 className="h3-b inline-flex items-center gap-3 text-relay-ink">
                <UsersRound className="size-6" strokeWidth={2.2} aria-hidden />
                참여자
              </h2>
              <span className="h2-b text-relay-accent">
                {participants.length} / {maxParticipants}
              </span>
            </div>
            <p className="caption-b mt-2 text-relay-muted">
              최소 {minParticipants}명부터 시작할 수 있어요.
            </p>

            <div className="mt-4 grid grid-cols-1 gap-3 xl:grid-cols-2">
              {participants.map((participant) => (
                <ParticipantTile
                  key={participant.userUuid}
                  participant={participant}
                  isMe={participant.userUuid === currentUserUuid}
                  canKick={isHost && participant.userUuid !== currentUserUuid}
                  isKicking={kickingTargetUuid === participant.userUuid}
                  onKick={() => kickParticipant(participant.userUuid)}
                />
              ))}
              {Array.from({ length: waitingSlotCount }).map(
                (_, waitingSlotIndex) => (
                  <div
                    key={`waiting-${waitingSlotIndex}`}
                    className="body-r grid min-h-11 place-items-center rounded-[14px] border border-dashed border-relay-accent px-5 py-3 text-relay-dash"
                  >
                    초대를 기다리는 중...
                  </div>
                ),
              )}
            </div>
            {kickError && (
              <p role="alert" className="caption-r mt-3 text-error">
                {kickError}
              </p>
            )}

            <div className="mt-6 grid gap-4 xl:grid-cols-2">
              <section className="rounded-2xl border border-relay-line bg-white/54 p-5 shadow-[inset_0_1px_0_rgb(255_255_255_/_82%)]">
                <h3 className="h3-b inline-flex items-center gap-2 text-relay-ink">
                  <Clock3
                    className="size-5 text-relay-accent"
                    strokeWidth={2.2}
                    aria-hidden
                  />
                  제한 시간
                </h3>
                <div className="mt-4 grid grid-cols-3 gap-3">
                  {timeLimitAllowedSeconds.map((seconds) => {
                    const isSelected = seconds === timeLimitSeconds;
                    return (
                      <button
                        key={seconds}
                        type="button"
                        onClick={() => changeTimeLimit(seconds)}
                        disabled={!isHost}
                        className={cn(
                          "body-b min-h-[48px] cursor-pointer rounded-xl border border-relay-line bg-relay-active text-relay-muted shadow-[0_7px_14px_rgba(184,121,22,0.12)] transition-colors duration-150 ease-out hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-60",
                          isSelected &&
                            "border-relay-accent bg-relay-accent text-relay-ink",
                        )}
                      >
                        {seconds}초
                      </button>
                    );
                  })}
                </div>
                {settingsError && (
                  <p role="alert" className="caption-r mt-3 text-error">
                    {settingsError}
                  </p>
                )}
              </section>

              <section className="rounded-2xl border border-relay-line bg-white/54 p-5 shadow-[inset_0_1px_0_rgb(255_255_255_/_82%)]">
                <h3 className="h3-b text-relay-ink">참여 조건</h3>
                <div className="h-full flex items-center">
                  <p className="text-l mb-1 text-relay-muted">
                    현재 {participants.length}명 참여 중 · 최소{" "}
                    {minParticipants}명 필요
                  </p>
                </div>
              </section>
            </div>

            {/* 시작 버튼 */}
            <div className="mt-6 flex flex-col gap-2">
              {isHost ? (
                <RelayButton
                  onClick={startGame}
                  disabled={!canStartGame}
                  size="lg"
                  className="w-full rounded-2xl shadow-[0_8px_20px_rgba(184,121,22,0.3)]"
                >
                  {startButtonLabel}
                </RelayButton>
              ) : (
                <div className="body-r grid min-h-14 place-items-center rounded-2xl border border-dashed border-relay-accent px-5 text-relay-muted">
                  방장이 게임을 시작할 때까지 기다려주세요
                </div>
              )}
            </div>
          </motion.section>
        </main>
      </motion.div>
    </section>
  );
}

interface ParticipantTileProps {
  participant: RelayRoomParticipantResponse;
  isMe: boolean;
  canKick: boolean;
  isKicking: boolean;
  onKick: () => void;
}

function ParticipantTile({
  participant,
  isMe,
  canKick,
  isKicking,
  onKick,
}: ParticipantTileProps) {
  const avatarChar = participant.nickname.slice(0, 1).toUpperCase();

  return (
    <div
      className={cn(
        "flex min-h-11 items-center gap-2.5 rounded-2xl border border-relay-line bg-relay-active px-5 py-3",
        !participant.connected && "opacity-60",
      )}
    >
      <span className="grid size-7 place-items-center rounded-full bg-relay-paper text-[12px] font-bold text-relay-ink">
        {avatarChar}
      </span>
      <span className="body-l flex-1 truncate text-relay-ink">
        {participant.nickname}
        {isMe && " (나)"}
      </span>
      {!participant.connected && (
        <span className="caption-r text-relay-muted">재연결 중...</span>
      )}
      {participant.host && (
        <span className="caption-b inline-flex items-center gap-1 rounded-full border border-relay-accent bg-relay-accent px-2 py-1 text-relay-ink">
          <Crown className="size-4" aria-hidden />
          방장
        </span>
      )}
      {canKick && (
        <button
          type="button"
          onClick={onKick}
          disabled={isKicking}
          aria-label={`${participant.nickname} 강퇴`}
          className="grid size-7 place-items-center rounded-full text-relay-muted transition-colors hover:bg-relay-paper hover:text-error disabled:cursor-not-allowed disabled:opacity-50"
        >
          <X className="size-4" aria-hidden />
        </button>
      )}
    </div>
  );
}
