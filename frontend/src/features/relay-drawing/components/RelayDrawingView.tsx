"use client";

import { useEffect } from "react";
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
  const roundDeadlines = useRelayDrawingStore((state) => state.roundDeadlines);
  const roundSubmitted = useRelayDrawingStore((state) => state.roundSubmitted);
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

  // 현재 라운드의 데드라인/제출 상태 — 라운드 전환 시 cross-round auto-submit 방지.
  const currentRoundDeadline = roundDeadlines[activeRoundKey];
  const isCurrentRoundSubmitted = roundSubmitted[activeRoundKey];

  // submitDrawing이 보낼 데이터(canvasIndex/part)가 store에 채워졌는지.
  const isAssignmentLoaded = currentPart !== null && currentRoundDeadline !== null;

  // 자동 제출: 데드라인 도달 + 이 라운드의 데드라인 수신 완료 + 미제출이면 보낸다.
  // roundDeadlines[activeRoundKey] === null이면 이 라운드의 PART_STARTED를 아직
  // 수신하지 않은 것이므로 발사하지 않는다 — stale 0초 방어.
  useEffect(() => {
    if (
      remainingSeconds === 0 &&
      currentRoundDeadline !== null &&
      !isCurrentRoundSubmitted &&
      !isSubmitting &&
      isAssignmentLoaded
    ) {
      void submitDrawing();
    }
  }, [
    remainingSeconds,
    currentRoundDeadline,
    isCurrentRoundSubmitted,
    isSubmitting,
    isAssignmentLoaded,
    submitDrawing,
  ]);

  // 게임 중 이탈 시 best-effort 자동 제출 — 가이드 §27a.
  // - 탭 닫기/새로고침: beforeunload에서 fire-and-forget. fetch가 끝까지 갈
  //   보장은 없지만, 가능한 만큼 시도한다(서버 fallback은 빈 제출이라 손해).
  // - 라우트 이동(뒤로가기, 다른 페이지 push): 컴포넌트 언마운트 시 cleanup이
  //   동일 핸들러를 호출. SPA 내 전환은 보통 fetch가 완료된다.
  // dismissalReason이 세팅된 상태(강퇴/방종료/중복세션)에서는 모달 확인 흐름의
  // 부산물이므로 제출 시도하지 않는다 — 어차피 서버가 이미 정리한 세션이다.
  useEffect(() => {
    const attemptSubmitOnLeave = () => {
      const store = useRelayDrawingStore.getState();
      if (
        store.roomCode &&
        store.roomStatus === "PLAYING" &&
        !store.roundSubmitted[store.activeRoundKey] &&
        !store.isSubmitting &&
        store.canvasIndex !== null &&
        store.currentPart !== null &&
        !store.dismissalReason
      ) {
        void submitDrawing();
      }
    };

    window.addEventListener("beforeunload", attemptSubmitOnLeave);
    return () => {
      window.removeEventListener("beforeunload", attemptSubmitOnLeave);
      attemptSubmitOnLeave();
    };
  }, [submitDrawing]);

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
