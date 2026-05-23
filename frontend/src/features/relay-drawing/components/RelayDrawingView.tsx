"use client";

import dynamic from "next/dynamic";
import { useEffect, useRef, useState } from "react";
import { ColorPanel, DrawingCompleteButton, MobileBrushOpacityBar, MobileColorBar, MobileToolBar, ProgressRail, ToolPanel, TopStatusBar, PhoneLauncherButton } from "@/shared/components";
import {
  DRAWING_COLORS,
  DRAWING_STROKE_WIDTH_OPTIONS,
} from "@/shared/constants";
import { useDrawingKeyboardShortcuts } from "@/shared/hooks";
import { cn } from "@/shared/libs";
import type { DrawingToolKey } from "@/shared/types";
import { RELAY_ROUND_ORDER, RELAY_ROUND_SEGMENTS, RELAY_STAGE_SIZE } from '../constants';
import { useRelayDrawingGame, useRelayTimer } from '../hooks';import { useRelayDrawingStore } from "../stores";
import PartTimeUpOverlay from "./PartTimeUpOverlay";

import RelayBgmToggle from "./RelayBgmToggle";
import RelayHowToPlayButton from "./RelayHowToPlayButton";

const RelayDrawingStage = dynamic(() => import("../RelayDrawingStage"), {
  ssr: false,
});

// 데스크탑(lg+) 그리기 화면은 1536×1024 디자인을 기준으로 절대 좌표로 배치되어
// 있다. 작은 viewport에선 디자인 그대로 두면 클리핑되므로, 부모 크기를 측정해
// 가로/세로 중 더 작은 비율로 scale을 동적으로 잡는다. 측정 전에는 0으로 두어
// 첫 프레임의 클리핑 노출을 막는다.
const DESKTOP_DESIGN_WIDTH = 1536;
const DESKTOP_DESIGN_HEIGHT = 1024;

export default function RelayDrawingView() {
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey);
  const isPartTimeUp = useRelayDrawingStore((state) => state.isPartTimeUp);
  const selectedToolKey = useRelayDrawingStore(
    (state) => state.selectedToolKey,
  );
  const selectedColor = useRelayDrawingStore((state) => state.selectedColor);
  const selectedOpacity = useRelayDrawingStore(
    (state) => state.selectedOpacity,
  );
  const strokeWidth = useRelayDrawingStore((state) => state.strokeWidth);
  const recentColors = useRelayDrawingStore((state) => state.recentColors);
  const roundLines = useRelayDrawingStore((state) => state.roundLines);
  const roundRedoStack = useRelayDrawingStore((state) => state.roundRedoStack);
  const setSelectedToolKey = useRelayDrawingStore(
    (state) => state.setSelectedToolKey,
  );
  const setSelectedColor = useRelayDrawingStore(
    (state) => state.setSelectedColor,
  );
  const setSelectedOpacity = useRelayDrawingStore(
    (state) => state.setSelectedOpacity,
  );
  const setStrokeWidth = useRelayDrawingStore((state) => state.setStrokeWidth);
  const undoLine = useRelayDrawingStore((state) => state.undoLine);
  const redoLine = useRelayDrawingStore((state) => state.redoLine);
  const clearRoundLines = useRelayDrawingStore(
    (state) => state.clearRoundLines,
  );
  const { remainingSeconds, formattedTime } = useRelayTimer();
  const {
    submitDrawing,
    isSubmitting,
    isSubmitted,
    submittedCount,
    totalCount,
  } = useRelayDrawingGame();

  const activeRound = RELAY_ROUND_SEGMENTS[activeRoundKey];
  const activeRoundIndex = RELAY_ROUND_ORDER.findIndex(
    (roundKey) => roundKey === activeRoundKey,
  );
  const isLastRound = activeRoundKey === "legs";
  const canUndoDrawing = roundLines[activeRoundKey].length > 0;
  const canRedoDrawing = roundRedoStack[activeRoundKey].length > 0;
  const isDrawingLocked = isSubmitting || isSubmitted || isPartTimeUp;
  const completionStatusText =
    isSubmitted && totalCount > 0 ? ` (${submittedCount}/${totalCount})` : "";
  const buttonLabel = (() => {
    if (isSubmitting) return "제출 중";
    if (isSubmitted) return `대기 중${completionStatusText}`;
    return isLastRound ? "완료하기" : `${activeRound.label} 저장하기`;
  })();
  const overlayMessage = isPartTimeUp
    ? "다음 파트를 준비하고 있어요"
    : isSubmitted
      ? "제출 완료! 다음 파트를 기다리는 중이에요"
      : isSubmitting
        ? "그림을 제출하고 있어요"
        : null;

  const handleSelectTool = (toolKey: DrawingToolKey) => {
    if (toolKey === "marker") return;
    setSelectedToolKey(toolKey);
  };

  const handleSubmitClick = () => {
    void submitDrawing();
  };

  useDrawingKeyboardShortcuts({
    enabled: !isDrawingLocked,
    onUndo: undoLine,
    onRedo: redoLine,
  });

  // 데스크탑 레이아웃 동적 스케일 — 부모 크기를 측정해 1536×1024 디자인이
  // 정확히 들어맞는 scale을 계산. 측정 전 0이면 인너가 사라져 클리핑/플래시를
  // 방지한다. ResizeObserver가 콜백에서 setState하므로 React Compiler effect-body
  // 동기 setState 규칙을 위반하지 않는다.
  const desktopWrapperRef = useRef<HTMLDivElement>(null);
  const [desktopScale, setDesktopScale] = useState(0);

  useEffect(() => {
    const wrapper = desktopWrapperRef.current;
    if (!wrapper) return;
    const updateScale = () => {
      const rect = wrapper.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) return;
      const widthRatio = rect.width / DESKTOP_DESIGN_WIDTH;
      const heightRatio = rect.height / DESKTOP_DESIGN_HEIGHT;
      setDesktopScale(Math.min(widthRatio, heightRatio, 1));
    };
    const raf = requestAnimationFrame(updateScale);
    const observer = new ResizeObserver(updateScale);
    observer.observe(wrapper);
    return () => {
      cancelAnimationFrame(raf);
      observer.disconnect();
    };
  }, []);

  return (
    <section
      className="relative min-h-screen overflow-y-auto text-[#30343b] lg:grid lg:h-screen lg:overflow-hidden"
      aria-label="릴레이 드로잉"
    >
      <div className="relative z-10 grid w-full gap-4 px-3 py-4 lg:hidden">
        <div className="flex items-center justify-end gap-2">
          <RelayHowToPlayButton className="size-11" />
          <RelayBgmToggle className="size-11" />
          <PhoneLauncherButton className="size-11" />
        </div>

        <div className="rounded-[22px] border border-[#ead7c9] bg-white/90 p-4 shadow-[0_10px_24px_rgb(129_89_54_/_14%)]">
          <div className="flex items-center justify-between gap-3">
            <p className="h2-b text-[#f45d8d]">
              {activeRoundIndex + 1}/{RELAY_ROUND_ORDER.length}
            </p>
            <div className="body-b inline-flex min-h-10 items-center rounded-full border border-[#ead7c9] bg-white px-4 text-[#f45d8d]">
              {formattedTime}
            </div>
          </div>
          <p className="body-b mt-3 text-[#30343b]">{activeRound.helperText}</p>
        </div>

        <MobileToolBar
          selectedToolKey={selectedToolKey}
          canUndoDrawing={canUndoDrawing}
          canRedoDrawing={canRedoDrawing}
          isDrawingLocked={isDrawingLocked}
          onSelectTool={handleSelectTool}
          onUndoDrawing={undoLine}
          onRedoDrawing={redoLine}
          onClearDrawing={clearRoundLines}
        />

        <div className="min-w-0 rounded-[18px] border border-[#ead7c9] bg-white p-3 shadow-[0_10px_24px_rgb(129_89_54_/_14%)]">
          <div
            className="relative mx-auto w-full overflow-hidden rounded-[8px] bg-white"
            style={{
              maxWidth: RELAY_STAGE_SIZE.width,
              aspectRatio: `${RELAY_STAGE_SIZE.width} / ${RELAY_STAGE_SIZE.height}`,
            }}
          >
            <RelayDrawingStage />
            {overlayMessage && (
              <div className="body-b absolute inset-0 grid place-items-center bg-[#fff4a7]/72 text-[#30343b]">
                {overlayMessage}
              </div>
            )}
          </div>
        </div>

        <MobileColorBar
          colors={DRAWING_COLORS}
          selectedColor={selectedColor}
          isDrawingLocked={isDrawingLocked}
          onSelectColor={setSelectedColor}
        />

        <MobileBrushOpacityBar
          strokeWidth={strokeWidth}
          strokeWidthOptions={DRAWING_STROKE_WIDTH_OPTIONS}
          selectedOpacity={selectedOpacity}
          isDrawingLocked={isDrawingLocked}
          onStrokeWidthChange={setStrokeWidth}
          onOpacityChange={setSelectedOpacity}
        />

        <DrawingCompleteButton
          onComplete={handleSubmitClick}
          disabled={isDrawingLocked}
          className="min-h-14 rounded-[16px]"
          label={buttonLabel}
        />
      </div>

      <div
        ref={desktopWrapperRef}
        className="relative hidden lg:block lg:h-full lg:w-full"
      >
        <div
          className="absolute left-1/2 top-1/2 origin-center"
          style={{
            width: DESKTOP_DESIGN_WIDTH,
            height: DESKTOP_DESIGN_HEIGHT,
            transform: `translate(-50%, -50%) scale(${desktopScale})`,
          }}
        >
          <TopStatusBar
            activeRoundIndex={activeRoundIndex}
            roundCount={RELAY_ROUND_ORDER.length}
            remainingSeconds={remainingSeconds}
            remainingTimeLabel={formattedTime}
            timerUnitLabel=""
            instructionText={activeRound.helperText}
          />

          <ColorPanel
            className={cn(isDrawingLocked && "pointer-events-none opacity-60")}
            colors={DRAWING_COLORS}
            selectedColor={selectedColor}
            selectedOpacity={selectedOpacity}
            strokeWidth={strokeWidth}
            strokeWidthOptions={DRAWING_STROKE_WIDTH_OPTIONS}
            recentColors={recentColors}
            onSelectColor={setSelectedColor}
            onOpacityChange={setSelectedOpacity}
            onStrokeWidthChange={setStrokeWidth}
          />

          <main className="absolute left-[345px] top-[188px] h-[720px] w-[848px]">
            <div className="absolute inset-0 rounded-[8px] bg-white shadow-[0_8px_42px_-10px_rgb(0_0_0_/_25%)]" />
            <div className="absolute inset-0 z-10 overflow-hidden rounded-[4px] bg-white">
              <RelayDrawingStage />
              {overlayMessage && (
                <div className="body-b absolute inset-0 grid place-items-center bg-[#fff4a7]/72 text-[#30343b]">
                  {overlayMessage}
                </div>
              )}
            </div>
          </main>

          <ToolPanel
            className={cn(isDrawingLocked && "pointer-events-none opacity-60")}
            selectedToolKey={selectedToolKey}
            canUndoDrawing={canUndoDrawing}
            canRedoDrawing={canRedoDrawing}
            onSelectTool={handleSelectTool}
            onUndoDrawing={undoLine}
            onRedoDrawing={redoLine}
            onClearDrawing={clearRoundLines}
          />

          <ProgressRail
            activeRoundIndex={activeRoundIndex}
            roundCount={RELAY_ROUND_ORDER.length}
          />

          <DrawingCompleteButton
            onComplete={handleSubmitClick}
            disabled={isDrawingLocked}
            className="absolute left-[1254px] top-[928px] h-[62px] w-[222px]"
            label={buttonLabel}
          />
        </div>
      </div>

      <PartTimeUpOverlay isVisible={isPartTimeUp} isLastRound={isLastRound} />
    </section>
  );
}
