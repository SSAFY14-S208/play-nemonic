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
  const undoLine = useRelayDrawingStore((state) => state.undoLine);
  const redoLine = useRelayDrawingStore((state) => state.redoLine);
  const { formattedTime, isExpiring, remainingSeconds } = useRelayTimer();
  const {
    submitDrawing,
    isSubmitting,
    isSubmitted,
    submittedCount,
    totalCount,
  } = useRelayDrawingGame();

  // 키보드 단축키 — Ctrl+Z (Cmd+Z) 되돌리기, Ctrl+Shift+Z / Ctrl+Y (Cmd+Shift+Z)
  // 다시 실행. 일반 입력 필드(input/textarea/contentEditable)에 포커스가 있을 땐
  // 스킵해 텍스트 편집을 방해하지 않는다.
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (!(event.ctrlKey || event.metaKey)) return
      const target = event.target as HTMLElement | null
      if (
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" ||
          target.isContentEditable)
      ) {
        return
      }
      const key = event.key.toLowerCase();
      if (key === "z" && !event.shiftKey) {
        event.preventDefault();
        undoLine();
      } else if ((key === "z" && event.shiftKey) || key === "y") {
        event.preventDefault();
        redoLine();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [undoLine, redoLine]);

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
    <section className="min-h-screen w-full bg-relay-background text-relay-ink">
      {/* lg+: 좌(도구) - 중(캔버스) - 우(타이머/진행도/제출) 3열 가로 배치.
          그 아래(모바일/태블릿): 캔버스를 최우선으로 두고 그 아래에 도구 + 우측
          정보 패널을 세로로 stack. 캔버스(848×720)는 RelayDrawingStage가
          ResizeObserver로 컨테이너 크기에 맞춰 비례 스케일하므로 좁은 viewport
          에서도 잘리지 않고 들어맞는다. */}
      <div className="mx-auto flex w-full max-w-360 flex-col items-stretch gap-4 px-4 py-4 lg:h-screen lg:flex-row lg:items-center lg:justify-center lg:gap-5 lg:px-6">
        {/* Canvas — lg+에선 가운데 고정폭, 그 아래에선 페이지 최상단 + 전체 폭 */}
        <main className="order-1 flex w-full flex-col overflow-hidden rounded-2xl bg-relay-paper shadow-[0_28px_60px_rgba(148,124,64,0.12)] lg:order-2 lg:max-w-212 lg:flex-none">
          <div className="flex w-full items-center px-4 pt-3">
            <RoundProgressBar />
          </div>
          <div className="aspect-848/720 w-full lg:h-180 lg:aspect-auto">
            <RelayDrawingStage />
          </div>
        </main>

        {/* 도구 — lg+에선 좌측, 그 아래에선 캔버스 아래에 가로 폭 유지 */}
        <aside className="order-2 flex w-full flex-col justify-center gap-4 rounded-3xl bg-relay-paper p-4 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)] sm:p-6 lg:order-1 lg:w-56.25 lg:flex-none">
          <DrawingToolPanel />
        </aside>

        {/* 타이머 + 진행도 + 제출 버튼 — lg+에선 우측, 그 아래에선 도구 아래 */}
        <div className="order-3 flex w-full flex-col gap-4 lg:w-67.5 lg:flex-none">
          <CountdownTimer
            formattedTime={formattedTime}
            isExpiring={isExpiring}
          />
          <aside className="rounded-3xl bg-relay-paper p-6 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
            <RoundProgressPanel />
          </aside>

          <button
            type="button"
            onClick={handleSubmitClick}
            disabled={buttonDisabled}
            className="body-b min-h-14 cursor-pointer rounded-2xl bg-relay-accent text-relay-ink shadow-[0_6px_16px_rgba(184,121,22,0.35)] transition-all hover:brightness-105 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:brightness-100 disabled:active:scale-100"
          >
            {buttonLabel}
          </button>
        </div>
      </div>
    </section>
  );
}
