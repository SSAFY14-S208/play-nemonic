"use client";

import dynamic from "next/dynamic";
import type { CSSProperties } from "react";

const RelayDrawingStage = dynamic(
  () => import("@/features/relay-drawing/RelayDrawingStage"),
  { ssr: false },
);

interface DrawingStageFrameProps {
  overlayMessage: string | null;
  className: string;
  style?: CSSProperties;
}

export default function DrawingStageFrame({
  overlayMessage,
  className,
  style,
}: DrawingStageFrameProps) {
  return (
    <div className={className} style={style}>
      <RelayDrawingStage />
      {overlayMessage && (
        <div className="body-b absolute inset-0 grid place-items-center bg-[#fff4a7]/72 text-[#30343b]">
          {overlayMessage}
        </div>
      )}
    </div>
  );
}
