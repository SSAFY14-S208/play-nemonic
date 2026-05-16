"use client";

import { useEffect, useRef, useState } from "react";

import { cn } from "@/shared/libs";

import {
  INFINITY_TEXT_FONT_SIZES,
  type InfinityToolKey,
} from "../constants";
import type { InfinityTextEditorState } from "../hooks/useInfinityDrawing";

interface InfinityTextEditorProps {
  state: InfinityTextEditorState;
  scaleRef: React.RefObject<number>;
  stagePosRef: React.RefObject<{ x: number; y: number }>;
  // textEditor가 떠 있는 동안 stage 이벤트 차단을 위해 부모에서 toolSnapshot 사용 가능.
  // 여기서는 editor 자체의 commit/cancel만 책임.
  onCommit: (text: string, fontSize: number) => void;
  onCancel: () => void;
  // 사용자가 도구를 바꾸면 자동 commit (현재 도구 변경 추적은 부모가 closeTextEditor 호출).
  // editingTool: 현재 도구 — text가 아니면 자동 commit.
  editingTool: InfinityToolKey;
}

// canvas world 좌표(state.x, state.y)를 화면 픽셀 좌표로 변환.
function worldToScreen(
  world: { x: number; y: number },
  scale: number,
  stagePos: { x: number; y: number },
) {
  return {
    x: world.x * scale + stagePos.x,
    y: world.y * scale + stagePos.y,
  };
}

export function InfinityTextEditor({
  state,
  scaleRef,
  stagePosRef,
  onCommit,
  onCancel,
  editingTool,
}: InfinityTextEditorProps) {
  const [text, setText] = useState<string>(state.initialText);
  const [fontSize, setFontSize] = useState<number>(state.fontSize);
  const [viewportSnapshot, setViewportSnapshot] = useState({
    scale: 1,
    stagePosition: { x: 0, y: 0 },
  });
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const screen = worldToScreen(
    { x: state.x, y: state.y },
    viewportSnapshot.scale,
    viewportSnapshot.stagePosition,
  );

  useEffect(() => {
    setViewportSnapshot({
      scale: scaleRef.current,
      stagePosition: stagePosRef.current,
    });
  }, [scaleRef, stagePosRef, state.x, state.y]);

  useEffect(() => {
    const ta = textareaRef.current;
    if (!ta) return;
    ta.focus();
    ta.setSelectionRange(ta.value.length, ta.value.length);
  }, []);

  // 도구가 text가 아닌 다른 도구로 바뀌면 자동 commit.
  useEffect(() => {
    if (editingTool !== "text" && editingTool !== "select") {
      onCommit(text, fontSize);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editingTool]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Escape") {
      e.preventDefault();
      onCancel();
    }
    // Shift+Enter는 줄바꿈, Enter 단독은 commit.
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      onCommit(text, fontSize);
    }
  };

  const handleBlur = () => {
    // popover 클릭으로 인한 blur는 무시 — popover는 onMouseDown preventDefault로 blur 방지.
    onCommit(text, fontSize);
  };

  const adjustedFontSize = fontSize * viewportSnapshot.scale;

  return (
    <div
      className="absolute z-[var(--z-modal)] pointer-events-auto"
      style={{ left: screen.x, top: screen.y }}
    >
      {/* 폰트 크기 popover — textarea 위쪽 */}
      <div
        className="absolute bottom-full left-0 mb-2 flex items-center gap-1 rounded-lg border border-canvas-border bg-canvas-panel shadow-md px-2 py-1"
        onMouseDown={(e) => e.preventDefault()}
      >
        <button
          type="button"
          onClick={() => setFontSize((s) => Math.max(8, s - 2))}
          className="w-6 h-6 flex items-center justify-center rounded hover:bg-canvas-active text-canvas-ink"
        >
          −
        </button>
        <input
          type="number"
          value={fontSize}
          min={8}
          max={256}
          onChange={(e) => {
            const n = Number(e.target.value);
            if (!Number.isNaN(n)) setFontSize(Math.max(8, Math.min(256, n)));
          }}
          className="w-12 text-center body-r text-fg-primary bg-transparent outline-none"
        />
        <button
          type="button"
          onClick={() => setFontSize((s) => Math.min(256, s + 2))}
          className="w-6 h-6 flex items-center justify-center rounded hover:bg-canvas-active text-canvas-ink"
        >
          +
        </button>
        <div className="w-px h-4 bg-canvas-border mx-1" />
        {INFINITY_TEXT_FONT_SIZES.map((preset) => (
          <button
            key={preset}
            type="button"
            onClick={() => setFontSize(preset)}
            className={cn(
              "px-1.5 h-6 rounded caption-r hover:bg-canvas-active text-canvas-ink",
              fontSize === preset && "bg-canvas-accent text-white",
            )}
          >
            {preset}
          </button>
        ))}
      </div>

      {/* 입력 영역 */}
      <textarea
        ref={textareaRef}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={handleBlur}
        rows={1}
        spellCheck={false}
        className="resize-none border border-canvas-accent rounded bg-white/80 outline-none px-1 py-0.5 leading-tight"
        style={{
          fontSize: `${adjustedFontSize}px`,
          color: state.color,
          minWidth: "2ch",
          fontFamily: "inherit",
        }}
      />
    </div>
  );
}
