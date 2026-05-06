import Image from "next/image";
import { Copy, Crown, QrCode } from "lucide-react";
import { relayPostItNote } from "@/shared/assets";
import { RELAY_ROOM_CODE, RELAY_TIME_LIMITS_SECONDS } from "../constants";
import { cn } from "@/shared/libs";

interface RelayLobbyViewProps {
  onStartGame: () => void;
}

const LOBBY_PARTICIPANTS = [
  { id: "host", name: "여우 (나)", avatar: "🦊", isHost: true },
  { id: "cat-1", name: "고양이", avatar: "🦊", isHost: false },
  { id: "cat-2", name: "고양이", avatar: "🦊", isHost: false },
];

const WAITING_SLOT_COUNT = 3;

export default function RelayLobbyView({ onStartGame }: RelayLobbyViewProps) {
  return (
    <section className="relative h-full overflow-hidden border border-relay-border bg-relay-background">
      <div className="relative mx-auto h-[900px] w-full max-w-[1440px] overflow-hidden">
        <Image
          src={relayPostItNote}
          alt=""
          aria-hidden
          priority
          className="absolute left-[6.8%] top-[18.1%] h-[61%] w-[39.5%] object-contain"
        />

        <div className="absolute left-[9.7%] top-[32.4%] flex h-[32%] w-[33.1%] flex-col items-center justify-center gap-4 rounded-[32px] px-10 py-[60px]">
          <p className="h2-b text-relay-ink/80">입장 코드</p>
          <p
            className="font-bold tracking-[8px] text-relay-ink"
            style={{ fontSize: "clamp(4.5rem, 7vw, 6rem)", lineHeight: 1 }}
          >
            {RELAY_ROOM_CODE}
          </p>
          <div className="mt-2 flex gap-10">
            <button
              type="button"
              className="body-b inline-flex min-h-[45px] items-center gap-1.5 rounded-full border border-relay-line bg-relay-active px-4 text-relay-accent-strong"
            >
              <Copy className="size-[17px]" aria-hidden />
              링크 복사
            </button>
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
              <span className="h3-b text-relay-accent">3/6</span>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3">
              {LOBBY_PARTICIPANTS.map((participant) => (
                <ParticipantTile
                  key={participant.id}
                  participant={participant}
                />
              ))}
              {Array.from({ length: WAITING_SLOT_COUNT }).map(
                (_, waitingSlotIndex) => (
                  <div
                    key={waitingSlotIndex}
                    className="caption-b grid min-h-14 place-items-center rounded-[14px] border border-dashed border-relay-accent text-relay-dash"
                  >
                    초대를 기다리는 중...
                  </div>
                ),
              )}
            </div>
          </section>

          <section className="rounded-[24px] bg-relay-paper px-8 py-5 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
            <h2 className="h3-b text-relay-muted">⏱ 제한 시간</h2>
            <div className="mt-5 grid grid-cols-3 gap-3">
              {RELAY_TIME_LIMITS_SECONDS.map((seconds) => {
                const isSelected = seconds === 45;

                return (
                  <button
                    key={seconds}
                    type="button"
                    className={cn(
                      "body-b min-h-12 rounded-[12px] border border-relay-line bg-relay-active text-relay-accent",
                      isSelected && "text-relay-ink",
                    )}
                  >
                    {seconds}초
                  </button>
                );
              })}
            </div>
          </section>

          <button
            type="button"
            onClick={onStartGame}
            className="body-b min-h-16 rounded-[16px] bg-relay-accent text-relay-ink shadow-[0_6px_16px_rgba(184,121,22,0.4)]"
          >
            🎨 게임 시작 (3명)
          </button>
        </div>
      </div>
    </section>
  );
}

function ParticipantTile({
  participant,
}: {
  participant: (typeof LOBBY_PARTICIPANTS)[number];
}) {
  return (
    <div className="flex min-h-14 items-center gap-3 rounded-[16px] border border-relay-line bg-relay-active px-3.5">
      <span className="grid size-9 place-items-center rounded-full bg-relay-active text-[18px]">
        {participant.avatar}
      </span>
      <span className="body-b flex-1 text-relay-ink">{participant.name}</span>
      {participant.isHost && (
        <span className="caption-b inline-flex items-center gap-1 rounded-full border border-relay-accent bg-relay-accent px-2 py-1 text-relay-ink">
          <Crown className="size-4" aria-hidden />
          방장
        </span>
      )}
    </div>
  );
}
