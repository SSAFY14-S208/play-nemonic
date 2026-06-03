"use client";

import PartTimeUpOverlay from "./sections/PartTimeUpOverlay";
import DesktopDrawingLayout from "./sections/DesktopDrawingLayout";
import MobileDrawingLayout from "./sections/MobileDrawingLayout";
import { useDesktopStageScale, useRelayDrawingViewState } from "./hooks";

export default function RelayDrawingView() {
  const drawingState = useRelayDrawingViewState();
  const {
    desktopWrapperRef,
    desktopScale,
    desktopDesignWidth,
    desktopDesignHeight,
  } = useDesktopStageScale();

  return (
    <section
      className="relative min-h-screen overflow-y-auto text-[#30343b] lg:grid lg:h-screen lg:overflow-hidden"
      aria-label="릴레이 드로잉 진행"
    >
      <MobileDrawingLayout drawingState={drawingState} />
      <DesktopDrawingLayout
        drawingState={drawingState}
        desktopWrapperRef={desktopWrapperRef}
        desktopScale={desktopScale}
        desktopDesignWidth={desktopDesignWidth}
        desktopDesignHeight={desktopDesignHeight}
      />
      <PartTimeUpOverlay
        isVisible={drawingState.isPartTimeUp}
        isLastRound={drawingState.isLastRound}
      />
    </section>
  );
}
