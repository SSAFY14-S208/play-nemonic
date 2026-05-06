"use client";

import { Fragment } from "react";
import { cn } from "@/shared/libs";
import { RELAY_ROUNDS, RELAY_ROUND_ORDER } from "../constants";
import { useRelayDrawingStore } from "../stores";

export default function RoundProgressBar() {
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey);
  const activeRoundIndex = RELAY_ROUND_ORDER.findIndex(
    (roundKey) => roundKey === activeRoundKey,
  );

  return (
    <div className="flex w-full items-center justify-center px-6 py-3">
      {RELAY_ROUNDS.map((round, index) => {
        const isDone = index < activeRoundIndex;
        const isActive = index === activeRoundIndex;

        return (
          <Fragment key={round.key}>
            <div className="flex flex-col items-center gap-1">
              <div
                className={cn(
                  "grid size-8 place-items-center rounded-full body-b",
                  isDone && "bg-relay-accent-strong text-fg-inverse",
                  isActive && "bg-relay-accent-strong text-fg-inverse",
                  !isDone && !isActive && "bg-relay-accent text-relay-ink",
                )}
              >
                {isDone ? "✓" : index + 1}
              </div>
              <span
                className={cn(
                  "caption-b",
                  isDone || isActive ? "text-relay-ink" : "text-relay-muted",
                )}
              >
                {round.label}
              </span>
            </div>

            {index < RELAY_ROUNDS.length - 1 && (
              <div
                className={cn(
                  "mx-2 mb-5 h-0.75 flex-1",
                  index < activeRoundIndex
                    ? "bg-relay-accent-strong"
                    : "bg-relay-accent",
                )}
              />
            )}
          </Fragment>
        );
      })}
    </div>
  );
}
