"use client";

import dynamic from "next/dynamic";
import { RELAY_ROUND_ORDER, RELAY_ROUND_SEGMENTS } from "../constants";
import { useRelayTimer } from "../hooks/useRelayTimer";
import { useRelayDrawingStore } from "../relayDrawingStore";
import CountdownTimer from "./CountdownTimer";
import DrawingToolPanel from "./DrawingToolPanel";
import RoundProgressBar from "./RoundProgressBar";
import RoundProgressPanel from "./RoundProgressPanel";

const RelayDrawingStage = dynamic(() => import("../RelayDrawingStage"), {
  ssr: false,
});

export default function RelayDrawingView() {
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey);
  const activeRoundIndex = RELAY_ROUND_ORDER.findIndex(
    (roundKey) => roundKey === activeRoundKey,
  );
  const completeRound = useRelayDrawingStore((state) => state.completeRound);
  const { formattedTime, isExpiring } = useRelayTimer();

  const activeRound = RELAY_ROUND_SEGMENTS[activeRoundKey];
  const isLastRound = activeRoundIndex === RELAY_ROUND_ORDER.length - 1;

  return (
    <section className="flex h-full w-full bg-relay-background text-relay-ink">
      <div className="flex h-full w-full items-center justify-center gap-5">
        <aside className="flex w-[225px] flex-col justify-center gap-4 rounded-[24px] bg-relay-paper p-6 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
          <DrawingToolPanel />
        </aside>

        <main className="flex w-[848px] flex-col overflow-hidden rounded-[16px] bg-relay-paper shadow-[0_28px_60px_rgba(148,124,64,0.12)]">
          <div className="flex items-center w-full px-4 pt-3">
            <RoundProgressBar />
          </div>
          <div className="h-[720px] w-full">
            <RelayDrawingStage />
          </div>
        </main>

        <div className="flex w-[270px] flex-col gap-4">
          <CountdownTimer
            formattedTime={formattedTime}
            isExpiring={isExpiring}
          />
          <aside className="rounded-[24px] bg-relay-paper p-6 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
            <RoundProgressPanel />
          </aside>

          <button
            type="button"
            onClick={completeRound}
            className="body-b min-h-14 rounded-[16px] bg-relay-accent text-relay-ink shadow-[0_6px_16px_rgba(184,121,22,0.35)]"
          >
            {isLastRound
              ? "결과 합치기 →"
              : `${activeRound.label} 저장하고 다음 →`}
          </button>
        </div>
      </div>
    </section>
  );
}
