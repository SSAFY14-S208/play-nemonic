"use client";

import { RELAY_COLORS } from "../constants";
import { useRelayDrawingStore } from "../stores";
import { cn } from "@/shared/libs";

const DRAWING_TOOL_BUTTONS = [
  { key: "pencil", label: "연필", icon: "✏️", action: "select" },
  { key: "eraser", label: "지우개", icon: "🧽", action: "select" },
  { key: "bucket", label: "채우기", icon: "🪣", action: "select" },
  { key: "undo", label: "되돌리기 (Ctrl+Z)", icon: "↩️", action: "undo" },
  { key: "redo", label: "다시 실행 (Ctrl+Shift+Z)", icon: "↪️", action: "redo" },
  { key: "clear", label: "비우기", icon: "🗑️", action: "clear" },
] as const;

const STROKE_WIDTH_OPTIONS = [3, 6, 10];

export default function DrawingToolPanel() {
  const selectedToolKey = useRelayDrawingStore(
    (state) => state.selectedToolKey,
  );
  const selectedColor = useRelayDrawingStore((state) => state.selectedColor);
  const strokeWidth = useRelayDrawingStore((state) => state.strokeWidth);
  const setSelectedToolKey = useRelayDrawingStore(
    (state) => state.setSelectedToolKey,
  );
  const setSelectedColor = useRelayDrawingStore(
    (state) => state.setSelectedColor,
  );
  const setStrokeWidth = useRelayDrawingStore((state) => state.setStrokeWidth);
  const undoLine = useRelayDrawingStore((state) => state.undoLine);
  const redoLine = useRelayDrawingStore((state) => state.redoLine);
  const clearRoundLines = useRelayDrawingStore(
    (state) => state.clearRoundLines,
  );

  return (
    <>
      <section className="grid gap-4">
        <p className="h4-b text-relay-ink">도구</p>
        <div className="grid grid-cols-3 gap-2">
          {DRAWING_TOOL_BUTTONS.map((tool) => {
            const isSelectableTool = tool.action === "select";
            const isActive = isSelectableTool && selectedToolKey === tool.key;

            return (
              <button
                key={tool.key}
                type="button"
                aria-label={tool.label}
                onClick={() => {
                  if (tool.action === "undo") {
                    undoLine();
                    return;
                  }
                  if (tool.action === "redo") {
                    redoLine();
                    return;
                  }
                  if (tool.action === "clear") {
                    clearRoundLines();
                    return;
                  }
                  setSelectedToolKey(tool.key);
                }}
                className={cn(
                  "grid size-[50px] cursor-pointer place-items-center rounded-[14px] border border-relay-line bg-relay-active text-[22px] transition-all hover:brightness-95 disabled:hover:brightness-100",
                  isActive &&
                    "border-relay-accent-strong bg-relay-accent shadow-sm",
                )}
              >
                {tool.icon}
              </button>
            );
          })}
        </div>
      </section>

      <section className="grid gap-4">
        <p className="h4-b text-relay-ink">굵기</p>
        <div className="grid grid-cols-3 gap-2">
          {STROKE_WIDTH_OPTIONS.map((strokeWidthOption) => (
            <button
              key={strokeWidthOption}
              type="button"
              aria-label={`${strokeWidthOption}px 굵기`}
              onClick={() => setStrokeWidth(strokeWidthOption)}
              className={cn(
                "grid min-h-8 cursor-pointer place-items-center rounded-[12px] border border-relay-line bg-relay-active transition-all hover:brightness-95 disabled:hover:brightness-100",
                strokeWidth === strokeWidthOption &&
                  "border-2 border-relay-line bg-relay-accent",
              )}
            >
              <span
                className="rounded-full bg-relay-dash"
                style={{
                  width: `${strokeWidthOption}px`,
                  height: `${strokeWidthOption}px`,
                }}
              />
            </button>
          ))}
        </div>
      </section>

      <section className="grid gap-4">
        <p className="h4-b text-relay-ink">색</p>
        <div className="flex flex-wrap gap-2">
          {RELAY_COLORS.map((color) => (
            <button
              key={color}
              type="button"
              aria-label={`${color} 색상`}
              onClick={() => setSelectedColor(color)}
              className={cn(
                "size-9 cursor-pointer rounded-full border border-transparent transition-transform hover:scale-110",
                selectedColor === color &&
                  "border-[3px] border-relay-accent-strong",
                color === "#ffffff" && "border-relay-dash",
              )}
              style={{ backgroundColor: color }}
            />
          ))}
        </div>
      </section>
    </>
  );
}
