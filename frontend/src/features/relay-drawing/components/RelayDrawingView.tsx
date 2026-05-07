"use client";

import { useEffect, useRef } from "react";
import dynamic from "next/dynamic";

import { RELAY_ROUND_SEGMENTS } from "../constants";
import { useRelayDrawingGame } from "../hooks/useRelayDrawingGame";
import { useRelayTimer } from "../hooks/useRelayTimer";
import { useRelayDrawingStore } from "../stores";
import CountdownTimer from "./CountdownTimer";
import DrawingToolPanel from "./DrawingToolPanel";
import RoundProgressBar from "./RoundProgressBar";
import RoundProgressPanel from "./RoundProgressPanel";

const RelayDrawingStage = dynamic(() => import("../RelayDrawingStage"), {
  ssr: false,
});

export default function RelayDrawingView() {
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey);
  const currentPart = useRelayDrawingStore((state) => state.currentPart);
  const partDeadlineAt = useRelayDrawingStore((state) => state.partDeadlineAt);
  const { formattedTime, isExpiring, remainingSeconds } = useRelayTimer();
  const {
    submitDrawing,
    isSubmitting,
    isSubmitted,
    submittedCount,
    totalCount,
  } = useRelayDrawingGame();

  const activeRound = RELAY_ROUND_SEGMENTS[activeRoundKey];
  const isLastRound = activeRoundKey === "legs";

  // submitDrawing이 보낼 데이터(canvasIndex/part/deadline)가 store에 채워졌는지.
  // 버튼 비활성에는 사용하지 않고, 자동 제출 게이트와 클릭 핸들러에서만 본다.
  const isAssignmentLoaded = currentPart !== null && partDeadlineAt !== null;

  // 자동 제출 freshness 잠금:
  // "양수 remainingSeconds로 한 번이라도 카운트다운한 partDeadlineAt"만 ref에
  // 기록한다. 라운드 전환 직후 setAssignment commit이 stale 0초와 함께 떨어지는
  // 경계, fetch 지연으로 직전 라운드 deadline이 과거인 채로 0초가 유지되는 경계,
  // clock skew로 deadline이 과거인 경우 — 이런 모든 시나리오에서 ref가 현재
  // partDeadlineAt과 일치하지 않아 자동 제출이 발사되지 않는다.
  // 정상 카운트다운에서는 매 tick마다 ref가 갱신되므로 0 도달 시점에 일치한다.
  const validDeadlineRef = useRef<string | null>(null);
  useEffect(() => {
    if (partDeadlineAt !== null && remainingSeconds > 0) {
      validDeadlineRef.current = partDeadlineAt;
    }
  }, [partDeadlineAt, remainingSeconds]);

  // 자동 제출: 데드라인 도달 + 아직 미제출이면 보낸다.
  useEffect(() => {
    if (
      remainingSeconds === 0 &&
      isAssignmentLoaded &&
      validDeadlineRef.current === partDeadlineAt &&
      !isSubmitting &&
      !isSubmitted
    ) {
      void submitDrawing();
    }
  }, [
    remainingSeconds,
    isAssignmentLoaded,
    partDeadlineAt,
    isSubmitting,
    isSubmitted,
    submitDrawing,
  ]);

  // 버튼은 "이 라운드에서 이미 제출했는가"만 체크한다.
  const buttonDisabled = isSubmitting || isSubmitted;

  const buttonLabel = (() => {
    if (isSubmitting) return "제출 중...";
    if (isSubmitted) {
      return totalCount > 0
        ? `다른 참여자 대기 중 (${submittedCount}/${totalCount})`
        : "대기 중...";
    }
    return isLastRound ? "완료" : `${activeRound.label} 저장하고 다음 →`;
  })();

  // 배정 미도착 시 클릭은 silent no-op. submitDrawing 내부의 canvasIndex 가드가
  // 안전망 역할을 하고, 첫 배정 fetch 지연(~100ms)은 사용자에게 보이지 않는다.
  const handleSubmitClick = () => {
    void submitDrawing();
  };

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
            onClick={handleSubmitClick}
            disabled={buttonDisabled}
            className="body-b min-h-14 cursor-pointer rounded-[16px] bg-relay-accent text-relay-ink shadow-[0_6px_16px_rgba(184,121,22,0.35)] transition-all hover:brightness-105 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:brightness-100 disabled:active:scale-100"
          >
            {buttonLabel}
          </button>
        </div>
      </div>
    </section>
  );
}
