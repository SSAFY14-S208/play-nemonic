"use client";

import {
  DrawingCompleteButton,
  MobileBrushOpacityBar,
  MobileColorBar,
  MobileToolBar,
  PhoneLauncherButton,
} from "@/shared/components";
import { DRAWING_COLORS, DRAWING_STROKE_WIDTH_OPTIONS } from "@/shared/constants";
import RelayBgmToggle from "@/features/relay-drawing/components/RelayBgmToggle";
import RelayHowToPlayButton from "@/features/relay-drawing/components/RelayHowToPlayButton";
import { RELAY_ROUND_ORDER, RELAY_STAGE_SIZE } from "@/features/relay-drawing/constants";

import type { RelayDrawingViewState } from "../../hooks";
import DrawingStageFrame from "../DrawingStageFrame";

interface MobileDrawingLayoutProps {
  drawingState: RelayDrawingViewState;
}

export default function MobileDrawingLayout({
  drawingState,
}: MobileDrawingLayoutProps) {
  return (
    <div className="relative z-10 grid w-full gap-4 px-3 py-4 lg:hidden">
      <div className="flex items-center justify-end gap-2">
        <RelayHowToPlayButton className="size-11" />
        <RelayBgmToggle className="size-11" />
        <PhoneLauncherButton className="size-11" />
      </div>

      <div className="rounded-[22px] border border-[#ead7c9] bg-white/90 p-4 shadow-[0_10px_24px_rgb(129_89_54_/_14%)]">
        <div className="flex items-center justify-between gap-3">
          <p className="h2-b text-[#f45d8d]">
            {drawingState.activeRoundIndex + 1}/{RELAY_ROUND_ORDER.length}
          </p>
          <div className="body-b inline-flex min-h-10 items-center rounded-full border border-[#ead7c9] bg-white px-4 text-[#f45d8d]">
            {drawingState.formattedTime}
          </div>
        </div>
        <p className="body-b mt-3 text-[#30343b]">
          {drawingState.activeRound.helperText}
        </p>
      </div>

      <MobileToolBar
        selectedToolKey={drawingState.selectedToolKey}
        canUndoDrawing={drawingState.canUndoDrawing}
        canRedoDrawing={drawingState.canRedoDrawing}
        isDrawingLocked={drawingState.isDrawingLocked}
        onSelectTool={drawingState.handleSelectTool}
        onUndoDrawing={drawingState.undoLine}
        onRedoDrawing={drawingState.redoLine}
        onClearDrawing={drawingState.clearRoundLines}
      />

      <div className="min-w-0 rounded-[18px] border border-[#ead7c9] bg-white p-3 shadow-[0_10px_24px_rgb(129_89_54_/_14%)]">
        <DrawingStageFrame
          className="relative mx-auto w-full overflow-hidden rounded-[8px] bg-white"
          style={{
            maxWidth: RELAY_STAGE_SIZE.width,
            aspectRatio: `${RELAY_STAGE_SIZE.width} / ${RELAY_STAGE_SIZE.height}`,
          }}
          overlayMessage={drawingState.overlayMessage}
        />
      </div>

      <MobileColorBar
        colors={DRAWING_COLORS}
        selectedColor={drawingState.selectedColor}
        isDrawingLocked={drawingState.isDrawingLocked}
        onSelectColor={drawingState.setSelectedColor}
      />

      <MobileBrushOpacityBar
        strokeWidth={drawingState.strokeWidth}
        strokeWidthOptions={DRAWING_STROKE_WIDTH_OPTIONS}
        selectedOpacity={drawingState.selectedOpacity}
        isDrawingLocked={drawingState.isDrawingLocked}
        onStrokeWidthChange={drawingState.setStrokeWidth}
        onOpacityChange={drawingState.setSelectedOpacity}
      />

      <DrawingCompleteButton
        onComplete={drawingState.handleSubmitDrawing}
        disabled={drawingState.isDrawingLocked}
        className="min-h-14 rounded-[16px]"
        label={drawingState.buttonLabel}
      />
    </div>
  );
}
