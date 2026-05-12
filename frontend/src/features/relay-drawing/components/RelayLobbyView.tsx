"use client";

import { ArrowLeft, Copy, Crown, X } from "lucide-react";

import { useUserStore } from "@/shared/stores";
import type { RelayRoomParticipantResponse } from "@/shared/types";

import { RELAY_ROOM_CODE } from "../constants";
import { useRelayLobby } from "../hooks";
import { useRelayDrawingStore } from "../stores";
import { cn } from "@/shared/libs";

import RelayButton from "./RelayButton";

const PANEL_CARD_CLASS =
  "rounded-2xl bg-relay-paper px-5 py-4 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)] sm:px-6";

export default function RelayLobbyView() {
  const roomCode = useRelayDrawingStore((state) => state.roomCode);
  const participants = useRelayDrawingStore((state) => state.participants);
  const maxParticipants = useRelayDrawingStore(
    (state) => state.maxParticipants,
  );
  const timeLimitSeconds = useRelayDrawingStore(
    (state) => state.timeLimitSeconds,
  );
  const timeLimitAllowedSeconds = useRelayDrawingStore(
    (state) => state.timeLimitAllowedSeconds,
  );
  const currentUserUuid = useUserStore((state) => state.userUuid);

  const {
    isHost,
    canStartGame,
    startError,
    isStarting,
    settingsError,
    kickingTargetUuid,
    kickError,
    copyConfirm,
    startGame,
    changeTimeLimit,
    kickParticipant,
    copyInviteLink,
    copyRoomCode,
    leaveRoom,
  } = useRelayLobby();

  const waitingSlotCount = Math.max(0, maxParticipants - participants.length);
  const startButtonLabel = isStarting
    ? "시작 중…"
    : `게임 시작 (${participants.length}명)`;

  return (
    <section className="relative isolate min-h-full overflow-hidden border border-relay-border bg-relay-background">
      <div className="mx-auto flex min-h-screen w-full max-w-6xl flex-col gap-5 px-3 py-5 sm:gap-6 sm:px-5 lg:h-screen lg:min-h-0 lg:gap-5 lg:px-[4%] lg:py-6">
        {/* 헤더 — 나가기 버튼 */}
        <header className="flex items-center">
          {/* nav 스타일 — RelayButton 흡수 대신 호버 피드백(translateY)만 통일. */}
          <button
            type="button"
            onClick={leaveRoom}
            className="body-b inline-flex cursor-pointer items-center gap-1.5 rounded-full border border-relay-line bg-relay-paper px-3.5 py-1.5 text-relay-ink shadow-sm transition-all hover:-translate-y-0.5 hover:brightness-95 disabled:hover:translate-y-0 disabled:hover:brightness-100"
          >
            <ArrowLeft className="size-5" aria-hidden />
            나가기
          </button>
        </header>

        {/* 메인 grid — 모바일은 1열 stack(order-{n}), lg+는 좌측 참여자(전체 높이) +
            우측 입장코드/시간/시작 stack. */}
        <main className="grid flex-1 grid-cols-1 content-start gap-3 lg:grid-cols-[5fr_7fr] lg:grid-rows-[auto_auto_1fr] lg:gap-4">
          {/* ② 입장 코드 — 모바일 최상단(공유 글랜스 가치), 데스크탑 우측 1행 */}
          <div
            className={cn(
              PANEL_CARD_CLASS,
              "order-1 flex flex-col items-center gap-3",
            )}
          >
            <p className="h3-b text-relay-ink/80">입장 코드</p>
            <p
              className="font-bold tracking-[0.4em] text-relay-ink"
              style={{ fontSize: "clamp(2rem, 4vw, 3.5rem)", lineHeight: 1 }}
            >
              {roomCode ?? RELAY_ROOM_CODE}
            </p>
            <div className="flex flex-wrap justify-center gap-3">
              <button
                type="button"
                onClick={copyInviteLink}
                className="body-b inline-flex min-h-9 cursor-pointer items-center gap-1.5 rounded-full border border-relay-line bg-relay-active px-3.5 text-relay-accent-strong transition-all hover:-translate-y-0.5 hover:brightness-95 disabled:hover:translate-y-0 disabled:hover:brightness-100"
              >
                <Copy className="size-[17px]" aria-hidden />
                {copyConfirm === "link" ? "복사됨" : "링크 복사"}
              </button>
              <button
                type="button"
                onClick={copyRoomCode}
                className="body-b inline-flex min-h-9 cursor-pointer items-center gap-1.5 rounded-full border border-relay-line bg-relay-active px-3.5 text-relay-accent-strong transition-all hover:-translate-y-0.5 hover:brightness-95 disabled:hover:translate-y-0 disabled:hover:brightness-100"
              >
                <Copy className="size-[17px]" aria-hidden />
                {copyConfirm === "roomCode" ? "복사됨" : "입장 코드 복사"}
              </button>
            </div>
          </div>

          {/* ③ 참여자 — 모바일 두번째, 데스크탑 좌측 (3행 전체 높이) */}
          <div
            className={cn(
              PANEL_CARD_CLASS,
              "order-2 lg:col-start-1 lg:row-start-1 lg:row-span-3",
            )}
          >
            <div className="flex items-center gap-1">
              <h2 className="h3-b text-relay-ink">참여자</h2>
              <span className="h3-b text-relay-accent">
                {participants.length}/{maxParticipants}
              </span>
            </div>
            <div className="mt-3 grid grid-cols-1 gap-2.5">
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
          </div>

          {/* ④ 제한 시간 — 모바일 세번째, 데스크탑 우측 2행 */}
          <div
            className={cn(
              PANEL_CARD_CLASS,
              "order-3 lg:col-start-2 lg:row-start-2",
            )}
          >
            <h2 className="h3-b text-relay-muted">⏱ 제한 시간</h2>
            <div className="mt-4 grid grid-cols-3 gap-2.5">
              {timeLimitAllowedSeconds.map((seconds) => {
                const isSelected = seconds === timeLimitSeconds;
                return (
                  <button
                    key={seconds}
                    type="button"
                    onClick={() => changeTimeLimit(seconds)}
                    disabled={!isHost}
                    className={cn(
                      "body-b min-h-10 cursor-pointer rounded-[12px] border border-relay-line bg-relay-active text-relay-accent transition-all hover:-translate-y-0.5 hover:brightness-95 disabled:cursor-not-allowed disabled:hover:translate-y-0 disabled:hover:brightness-100",
                      isSelected &&
                        "border-relay-accent bg-relay-accent/20 text-relay-ink",
                      !isHost && !isSelected && "opacity-60",
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
          </div>

          {/* ⑤ 시작 버튼 / 대기 메시지 — 모바일 마지막(엄지 영역), 데스크탑 우측 3행 */}
          <div className="order-4 flex flex-col gap-2 lg:col-start-2 lg:row-start-3 lg:self-end">
            {isHost ? (
              <RelayButton
                onClick={startGame}
                disabled={!canStartGame}
                size="lg"
                className="rounded-2xl shadow-[0_6px_16px_rgba(184,121,22,0.4)]"
              >
                {startButtonLabel}
              </RelayButton>
            ) : (
              <div className="body-r grid min-h-13 place-items-center rounded-2xl border border-dashed border-relay-accent px-5 text-relay-muted">
                방장이 게임을 시작할 때까지 기다려주세요
              </div>
            )}
            {startError && (
              <p role="alert" className="caption-r text-error">
                {startError}
              </p>
            )}
          </div>
        </main>
      </div>
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
  // 닉네임 첫 글자를 아바타로 사용 — 백엔드가 별도 아바타 데이터를 주지 않아
  // 임시로 첫 글자를 동그라미에 띄운다. 디자인이 별도 아바타 시스템을 정의하면
  // 그때 교체한다.
  const avatarChar = participant.nickname.slice(0, 1).toUpperCase();

  return (
    <div
      className={cn(
        "flex min-h-11 items-center gap-2.5 rounded-2xl border border-relay-line bg-relay-active px-3",
        !participant.connected && "opacity-60",
      )}
    >
      <span className="grid size-7 place-items-center rounded-full bg-relay-paper text-[12px] font-bold text-relay-ink">
        {avatarChar}
      </span>
      <span className="body-b flex-1 truncate text-relay-ink">
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
