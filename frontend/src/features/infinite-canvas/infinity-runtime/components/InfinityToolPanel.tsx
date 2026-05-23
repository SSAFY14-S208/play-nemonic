"use client";

import {
  Circle,
  Eraser,
  Hand,
  Lasso,
  MousePointer2,
  PaintBucket,
  Pencil,
  Redo2,
  Square,
  Trash2,
  Type,
  Undo2,
  WandSparkles,
} from "lucide-react";

import {
  DrawingColorSwatch,
  DrawingStrokeWidthPicker,
} from "@/shared/components";
import {
  DRAWING_COLORS,
  DRAWING_STROKE_WIDTH_OPTIONS,
} from "@/shared/constants";
import { cn } from "@/shared/libs";

import type { InfinityToolKey } from '..';

interface InfinityToolPanelProps {
  drawing: {
    tool: InfinityToolKey;
    color: string;
    strokeWidth: number;
    canUndo: boolean;
    canRedo: boolean;
    setTool: (tool: InfinityToolKey) => void;
    setColor: (color: string) => void;
    setStrokeWidth: (width: number) => void;
    undo: () => void;
    redo: () => void;
    clearAll: () => void;
  };
  onAiStickerClick?: () => void;
  isAiStickerOpen?: boolean;
}

type ToolIcon = React.ComponentType<{ size?: number; className?: string }>;

// lucide-react는 stroke 기반 아이콘이라 채워진 변형이 없음 → fill="currentColor"로 단색 채움.
function SquareFill({ size, className }: { size?: number; className?: string }) {
  return <Square size={size} className={className} fill="currentColor" />;
}
function CircleFill({ size, className }: { size?: number; className?: string }) {
  return <Circle size={size} className={className} fill="currentColor" />;
}

const TOOLS: {
  key: InfinityToolKey;
  label: string;
  Icon: ToolIcon;
}[] = [
  { key: "pen", label: "펜", Icon: Pencil },
  { key: "eraser", label: "픽셀 지우개", Icon: Eraser },
  { key: "bucket", label: "채우기", Icon: PaintBucket },
  { key: "select", label: "객체 선택 (V)", Icon: MousePointer2 },
  { key: "hand", label: "캔버스 이동 (H)", Icon: Hand },
  { key: "select-eraser", label: "선택 지우개", Icon: Lasso },
  { key: "shape-rect", label: "사각형", Icon: Square },
  { key: "shape-ellipse", label: "원", Icon: Circle },
  { key: "shape-rect-fill", label: "채워진 사각형", Icon: SquareFill },
  { key: "shape-ellipse-fill", label: "채워진 원", Icon: CircleFill },
  { key: "text", label: "텍스트 (T)", Icon: Type },
];

export function InfinityToolPanel({
  drawing,
  onAiStickerClick,
  isAiStickerOpen = false,
}: InfinityToolPanelProps) {
  const {
    tool,
    color,
    strokeWidth,
    canUndo,
    canRedo,
    setTool,
    setColor,
    setStrokeWidth,
    undo,
    redo,
    clearAll,
  } = drawing;

  return (
    <aside className="fixed left-4 top-1/2 z-10 flex w-[206px] -translate-y-1/2 flex-col gap-3 rounded-[24px] border border-canvas-border bg-white/92 p-4 shadow-[0_12px_28px_rgb(67_102_148_/_18%)] backdrop-blur">
      {/* Undo / Redo / Clear 버튼 */}
      <div className="flex gap-1">
        <button
          title="실행 취소 (Ctrl+Z)"
          onClick={undo}
          disabled={!canUndo}
          className="w-9 h-9 flex items-center justify-center rounded-xl hover:bg-canvas-active disabled:opacity-30 transition-colors text-canvas-ink"
        >
          <Undo2 size={15} />
        </button>
        <button
          title="다시 실행 (Ctrl+Shift+Z)"
          onClick={redo}
          disabled={!canRedo}
          className="w-9 h-9 flex items-center justify-center rounded-xl hover:bg-canvas-active disabled:opacity-30 transition-colors text-canvas-ink"
        >
          <Redo2 size={15} />
        </button>
        <button
          title="전체 지우기"
          onClick={clearAll}
          className="w-9 h-9 flex items-center justify-center rounded-xl hover:bg-red-100 text-red-500 transition-colors"
        >
          <Trash2 size={15} />
        </button>
      </div>

      <div className="h-px bg-canvas-border mx-1" />

      {/* canvas 툴 */}
      <div className="grid grid-cols-3 gap-1.5">
        {TOOLS.map(({ key, label, Icon }) => (
          <button
            key={key}
            title={label}
            onClick={() => setTool(key)}
            className={cn(
              "mx-auto flex h-11 w-full items-center justify-center rounded-[14px] transition-colors",
              tool === key
                ? "bg-[#eaf3ff] text-[#1f57c8] ring-2 ring-[#5dc7f2] ring-offset-1 ring-offset-white shadow-[0_6px_14px_rgba(46,115,242,0.18)]"
                : "hover:bg-canvas-active text-canvas-ink",
            )}
          >
            <Icon size={18} />
          </button>
        ))}
        <button
          title="AI 스티커 생성"
          onClick={onAiStickerClick}
          className={cn(
            "mx-auto flex h-11 w-full items-center justify-center rounded-[14px] transition-colors",
            isAiStickerOpen
              ? "bg-[#eaf3ff] text-[#1f57c8] ring-2 ring-[#5dc7f2] ring-offset-1 ring-offset-white shadow-[0_6px_14px_rgba(46,115,242,0.18)]"
              : "hover:bg-canvas-active text-canvas-ink",
          )}
        >
          <WandSparkles size={18} />
        </button>
      </div>

      <div className="h-px bg-canvas-border mx-1" />

      {/* 선 굵기 */}
      <DrawingStrokeWidthPicker
        className="grid grid-cols-5 gap-1.5 py-1"
        selectedStrokeWidth={strokeWidth}
        strokeWidthOptions={DRAWING_STROKE_WIDTH_OPTIONS}
        onStrokeWidthChange={setStrokeWidth}
      />

      <div className="h-px bg-canvas-border mx-1" />

      {/* 색상 선택 */}
      <div className="grid grid-cols-4 gap-2 p-1">
        {DRAWING_COLORS.map((swatch) => (
          <DrawingColorSwatch
            key={swatch}
            color={swatch}
            selected={color === swatch}
            shape="square"
            onSelectColor={setColor}
          />
        ))}
      </div>
    </aside>
  );
}
