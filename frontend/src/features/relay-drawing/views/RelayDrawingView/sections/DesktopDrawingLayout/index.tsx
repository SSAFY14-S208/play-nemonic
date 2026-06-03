"use client";

import {
  ColorPanel,
  DrawingCompleteButton,
  ProgressRail,
  ToolPanel,
  TopStatusBar,
} from "@/shared/components";
import { DRAWING_COLORS, DRAWING_STROKE_WIDTH_OPTIONS } from "@/shared/constants";
import { cn } from "@/shared/libs";
import { RELAY_ROUND_ORDER } from "@/features/relay-drawing/constants";
import type { RefObject } from "react";

import type { RelayDrawingViewState } from "../../hooks";
import DrawingStageFrame from "../DrawingStageFrame";

interface DesktopDrawingLayoutProps {
  drawingState: RelayDrawingViewState;
  desktopWrapperRef: RefObject<HTMLDivElement | null>;
  desktopScale: number;
  desktopDesignWidth: number;
  desktopDesignHeight: number;
}

export default function DesktopDrawingLayout({
  drawingState,
  desktopWrapperRef,
  desktopScale,
  desktopDesignWidth,
  desktopDesignHeight,
}: DesktopDrawingLayoutProps) {
  return (
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
          activeRoundIndex={drawingState.activeRoundIndex}
          roundCount={RELAY_ROUND_ORDER.length}
          remainingSeconds={drawingState.remainingSeconds}
          remainingTimeLabel={drawingState.formattedTime}
          timerUnitLabel=""
          instructionText={drawingState.activeRound.helperText}
        />

        <ColorPanel
          className={cn(drawingState.isDrawingLocked && "pointer-events-none opacity-60")}
          colors={DRAWING_COLORS}
          selectedColor={drawingState.selectedColor}
          selectedOpacity={drawingState.selectedOpacity}
          strokeWidth={drawingState.strokeWidth}
          strokeWidthOptions={DRAWING_STROKE_WIDTH_OPTIONS}
          recentColors={drawingState.recentColors}
          onSelectColor={drawingState.setSelectedColor}
          onOpacityChange={drawingState.setSelectedOpacity}
          onStrokeWidthChange={drawingState.setStrokeWidth}
        />

        <main className="absolute left-[345px] top-[188px] h-[720px] w-[848px]">
          <div className="absolute inset-0 rounded-[8px] bg-white shadow-[0_8px_42px_-10px_rgb(0_0_0_/_25%)]" />
          <DrawingStageFrame
            className="absolute inset-0 z-10 overflow-hidden rounded-[4px] bg-white"
            overlayMessage={drawingState.overlayMessage}
          />
        </main>

        <ToolPanel
          className={cn(drawingState.isDrawingLocked && "pointer-events-none opacity-60")}
          selectedToolKey={drawingState.selectedToolKey}
          canUndoDrawing={drawingState.canUndoDrawing}
          canRedoDrawing={drawingState.canRedoDrawing}
          onSelectTool={drawingState.handleSelectTool}
          onUndoDrawing={drawingState.undoLine}
          onRedoDrawing={drawingState.redoLine}
          onClearDrawing={drawingState.clearRoundLines}
        />

        <ProgressRail
          activeRoundIndex={drawingState.activeRoundIndex}
          roundCount={RELAY_ROUND_ORDER.length}
        />

        <DrawingCompleteButton
          onComplete={drawingState.handleSubmitDrawing}
          disabled={drawingState.isDrawingLocked}
          className="absolute left-[1254px] top-[928px] h-[62px] w-[222px]"
          label={drawingState.buttonLabel}
        />
      </div>
    </div>
  );
}
