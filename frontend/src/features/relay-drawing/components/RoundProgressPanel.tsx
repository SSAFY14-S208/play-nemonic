"use client";

import { RELAY_ROUND_ORDER, RELAY_ROUNDS } from "../constants";
import { useRelayDrawingStore } from "../relayDrawingStore";
import { cn } from "@/shared/libs";

const DRAWING_PARTICIPANTS = [
  { id: "fox", avatar: "🦊", name: "여우 (나)" },
  { id: "cat", avatar: "🐱", name: "고양이" },
  { id: "bear", avatar: "🐻", name: "곰돌이" },
] as const;

export default function RoundProgressPanel() {
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey);
  const roundLines = useRelayDrawingStore((state) => state.roundLines);

  const activeRoundIndex = RELAY_ROUND_ORDER.findIndex(
    (roundKey) => roundKey === activeRoundKey,
  );

  return (
    <div className="flex h-full flex-col gap-4">
      <p className="h4-b text-relay-ink">함께하는 친구들</p>
      <div className="grid gap-4">
        {DRAWING_PARTICIPANTS.map((participant) => (
          <div
            key={participant.id}
            className="flex min-h-12 items-center gap-3 rounded-[12px] border border-relay-line bg-relay-active px-3 text-relay-accent-strong"
          >
            <span className="grid size-7 place-items-center rounded-full border border-relay-line bg-relay-active">
              {participant.avatar}
            </span>
            <span className="body-b">{participant.name}</span>
          </div>
        ))}
      </div>

      <div className="caption-r mt-auto rounded-[14px] bg-relay-accent px-4 py-3 text-relay-ink">
        <p className="caption-b">💡 팁</p>
        <p className="mt-1">
          이전 사람 그림의 하단 일부만 보여요. 마음껏 상상해서 이어 그려보세요!
        </p>
      </div>
    </div>
  );
}
