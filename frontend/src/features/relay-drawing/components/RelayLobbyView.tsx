"use client";

import { Copy, Crown, QrCode } from "lucide-react";

import { useUserStore } from "@/shared/stores";
import type { RelayRoomParticipantResponse } from "@/shared/types";

import { RELAY_ROOM_CODE, RELAY_TIME_LIMITS_SECONDS } from "../constants";
import { useRelayLobby } from "../hooks";
import { useRelayDrawingStore } from "../stores";
import { cn } from "@/shared/libs";
import { PostItNote } from "@/shared/components";

export default function RelayLobbyView() {
  const roomCode = useRelayDrawingStore((state) => state.roomCode);
  const participants = useRelayDrawingStore((state) => state.participants);
  const maxParticipants = useRelayDrawingStore((state) => state.maxParticipants);
  const timeLimitSeconds = useRelayDrawingStore((state) => state.timeLimitSeconds);
  const currentUserUuid = useUserStore((state) => state.userUuid);

  const {
    isHost,
    canStartGame,
    startError,
    isStarting,
    settingsError,
    copyConfirm,
    startGame,
    changeTimeLimit,
    copyInviteLink,
  } = useRelayLobby();

  const waitingSlotCount = Math.max(0, maxParticipants - participants.length);
  const startButtonLabel = isStarting
    ? "시작 중…"
    : `🎨 게임 시작 (${participants.length}명)`;

  return (
    <section className="relative h-full overflow-hidden border border-relay-border bg-relay-background">
      <div className="relative mx-auto h-[900px] w-full max-w-[1440px] overflow-hidden">
        <PostItNote
          title="입장 코드를 담은 노란 포스트잇 배경 이미지"
          className="absolute left-[6.8%] top-[18.1%] h-[61%] w-[39.5%] text-[#FFE787]"
        />

        <div className="absolute left-[9.7%] top-[32.4%] flex h-[32%] w-[33.1%] flex-col items-center justify-center gap-4 rounded-[32px] px-10 py-[60px]">
          <p className="h2-b text-relay-ink/80">입장 코드</p>
          <p
            className="font-bold tracking-[8px] text-relay-ink"
            style={{ fontSize: "clamp(4.5rem, 7vw, 6rem)", lineHeight: 1 }}
          >
            {roomCode ?? RELAY_ROOM_CODE}
          </p>
          <div className="mt-2 flex gap-10">
            <button
              type="button"
              onClick={copyInviteLink}
              className="body-b inline-flex min-h-[45px] items-center gap-1.5 rounded-full border border-relay-line bg-relay-active px-4 text-relay-accent-strong"
            >
              <Copy className="size-[17px]" aria-hidden />
              {copyConfirm ? "복사됨" : "링크 복사"}
            </button>
            {/* TODO(차기): QR 코드 모달. 현재는 시각 요소만 유지 */}
            <button
              type="button"
              className="body-b inline-flex min-h-[45px] items-center gap-1.5 rounded-full border border-relay-line bg-relay-active px-4 text-relay-accent-strong"
            >
              <QrCode className="size-[17px]" aria-hidden />
              QR 코드
            </button>
          </div>
        </div>

        <div className="absolute left-[49.9%] top-[19.2%] flex h-[65.4%] w-[43.3%] flex-col gap-5">
          <section className="rounded-[24px] bg-relay-paper px-6 py-5 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
            <div className="flex items-center gap-1">
              <h2 className="h3-b text-relay-ink">참여자</h2>
              <span className="h3-b text-relay-accent">
                {participants.length}/{maxParticipants}
              </span>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3">
              {participants.map((participant) => (
                <ParticipantTile
                  key={participant.userUuid}
                  participant={participant}
                  isMe={participant.userUuid === currentUserUuid}
                />
              ))}
              {Array.from({ length: waitingSlotCount }).map((_, waitingSlotIndex) => (
                <div
                  key={`waiting-${waitingSlotIndex}`}
                  className="caption-b grid min-h-14 place-items-center rounded-[14px] border border-dashed border-relay-accent text-relay-dash"
                >
                  초대를 기다리는 중...
                </div>
              ))}
            </div>
          </section>

          <section className="rounded-[24px] bg-relay-paper px-8 py-5 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
            <h2 className="h3-b text-relay-muted">⏱ 제한 시간</h2>
            <div className="mt-5 grid grid-cols-3 gap-3">
              {RELAY_TIME_LIMITS_SECONDS.map((seconds) => {
                const isSelected = seconds === timeLimitSeconds;

                return (
                  <button
                    key={seconds}
                    type="button"
                    onClick={() => changeTimeLimit(seconds)}
                    disabled={!isHost}
                    className={cn(
                      "body-b min-h-12 rounded-[12px] border border-relay-line bg-relay-active text-relay-accent disabled:cursor-not-allowed",
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
          </section>

          {isHost ? (
            <button
              type="button"
              onClick={startGame}
              disabled={!canStartGame}
              className="body-b min-h-16 rounded-[16px] bg-relay-accent text-relay-ink shadow-[0_6px_16px_rgba(184,121,22,0.4)] disabled:opacity-45"
            >
              {startButtonLabel}
            </button>
          ) : (
            <p className="body-r min-h-16 grid place-items-center text-relay-muted">
              방장이 게임을 시작할 때까지 기다려주세요
            </p>
          )}
          {startError && (
            <p role="alert" className="caption-r text-error">
              {startError}
            </p>
          )}
        </div>
      </div>
    </section>
  );
}

interface ParticipantTileProps {
  participant: RelayRoomParticipantResponse;
  isMe: boolean;
}

function ParticipantTile({ participant, isMe }: ParticipantTileProps) {
  // 닉네임 첫 글자를 아바타로 사용 — 백엔드가 별도 아바타 데이터를 주지 않아
  // 임시로 첫 글자를 동그라미에 띄운다. 디자인이 별도 아바타 시스템을 정의하면
  // 그때 교체한다.
  const avatarChar = participant.nickname.slice(0, 1).toUpperCase();

  return (
    <div
      className={cn(
        "flex min-h-14 items-center gap-3 rounded-[16px] border border-relay-line bg-relay-active px-3.5",
        !participant.connected && "opacity-60",
      )}
    >
      <span className="grid size-9 place-items-center rounded-full bg-relay-paper text-[14px] font-bold text-relay-ink">
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
    </div>
  );
}
