"use client";

import dynamic from "next/dynamic";
import {
  ColorPanel,
  DrawingCompleteButton,
  MobileBrushOpacityBar,
  MobileColorBar,
  MobileToolBar,
  PhoneLauncherButton,
  ProgressRail,
  ToolPanel,
  TopStatusBar,
} from "@/shared/components";
import { DRAWING_COLORS, DRAWING_STROKE_WIDTH_OPTIONS } from "@/shared/constants";
import { cn } from "@/shared/libs";
import { RELAY_ROUND_ORDER, RELAY_STAGE_SIZE } from "@/features/relay-drawing/constants";
import RelayBgmToggle from "@/features/relay-drawing/components/RelayBgmToggle";
import RelayHowToPlayButton from "@/features/relay-drawing/components/RelayHowToPlayButton";

import PartTimeUpOverlay from "./sections/PartTimeUpOverlay";
import { useDesktopStageScale, useRelayDrawingViewState } from "./hooks";

const RelayDrawingStage = dynamic(() => import("../../RelayDrawingStage"), {
  ssr: false,
});

export default function RelayDrawingView() {
  const {
    selectedToolKey,
    selectedColor,
    selectedOpacity,
    strokeWidth,
    recentColors,
    setSelectedColor,
    setSelectedOpacity,
    setStrokeWidth,
    undoLine,
    redoLine,
    clearRoundLines,
    canUndoDrawing,
    canRedoDrawing,
    isDrawingLocked,
    isPartTimeUp,
    activeRound,
    activeRoundIndex,
    isLastRound,
    remainingSeconds,
    formattedTime,
    handleSelectTool,
    handleSubmitDrawing,
    buttonLabel,
    overlayMessage,
  } = useRelayDrawingViewState();
  const {
    desktopWrapperRef,
    desktopScale,
    desktopDesignWidth,
    desktopDesignHeight,
  } = useDesktopStageScale();

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
          onComplete={handleSubmitDrawing}
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
            width: desktopDesignWidth,
            height: desktopDesignHeight,
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
            onComplete={handleSubmitDrawing}
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
