"use client";

import { cn } from "@/shared/libs";
import { useUserStore } from "@/shared/stores";

import { useRelayDrawingStore } from "../stores";

export default function RoundProgressPanel() {
  const participants = useRelayDrawingStore((state) => state.participants);
  const currentUserUuid = useUserStore((state) => state.userUuid);

  return (
    <div className="flex h-full flex-col gap-4">
      <p className="h4-b text-relay-ink">함께하는 친구들</p>
      <div className="grid gap-4">
        {participants.map((participant) => (
          <div
            key={participant.userUuid}
            className={cn(
              "flex min-h-12 items-center gap-3 rounded-[12px] border border-relay-line bg-relay-active px-3",
              !participant.connected && "opacity-60",
            )}
          >
            <span className="grid size-7 place-items-center rounded-full bg-relay-paper caption-b text-relay-ink">
              {participant.nickname.slice(0, 1).toUpperCase()}
            </span>
            <span className="body-b flex-1 truncate text-relay-accent-strong">
              {participant.nickname}
              {participant.userUuid === currentUserUuid && " (나)"}
            </span>
            {!participant.connected && (
              <span className="caption-r text-relay-muted">재연결 중...</span>
            )}
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
