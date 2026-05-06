"use client";

import dynamic from "next/dynamic";
import {
  RELAY_ROUND_ORDER,
  RELAY_ROUND_SEGMENTS,
} from "../constants";
import { useRelayDrawingStore } from "../relayDrawingStore";
import DrawingToolPanel from "./DrawingToolPanel";
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

  const activeRound = RELAY_ROUND_SEGMENTS[activeRoundKey];
  const isLastRound = activeRoundIndex === RELAY_ROUND_ORDER.length - 1;

  return (
    <section className="relative h-full overflow-hidden bg-relay-background text-relay-ink">
      <div className="relative mx-auto h-full w-full max-w-[1440px] overflow-hidden">
        <aside className="absolute left-[33px] top-[184px] flex h-[481px] w-[225px] flex-col justify-center gap-4 rounded-[24px] bg-relay-paper p-6 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
          <DrawingToolPanel />
        </aside>

        <main className="absolute left-[273px] top-[90px] h-[720px] w-[848px] overflow-hidden rounded-[16px] bg-relay-paper shadow-[0_28px_60px_rgba(148,124,64,0.12)]">
          <RelayDrawingStage />
        </main>

        <aside className="absolute left-[1136px] top-[143px] h-[625px] w-[270px] overflow-hidden rounded-[24px] bg-relay-paper p-6 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
          <RoundProgressPanel />
        </aside>

        <button
          type="button"
          onClick={completeRound}
          className="body-b absolute left-[1136px] top-[792px] min-h-14 w-[270px] rounded-[16px] bg-relay-accent text-relay-ink shadow-[0_6px_16px_rgba(184,121,22,0.35)]"
        >
          {isLastRound
            ? "결과 합치기 →"
            : `${activeRound.label} 저장하고 다음 →`}
        </button>
      </div>
    </section>
  );
}
