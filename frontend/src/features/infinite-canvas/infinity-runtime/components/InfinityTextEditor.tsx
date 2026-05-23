"use client";

import { useEffect, useRef, useState } from "react";

import { cn } from "@/shared/libs";

import { INFINITY_COLORS, INFINITY_TEXT_DEFAULT_COLOR, INFINITY_TEXT_FONT_FAMILIES, INFINITY_TEXT_FONT_SIZES, type InfinityToolKey } from '..';
import type { InfinityTextEditorCommitValue, InfinityTextEditorState } from '../hooks';

interface InfinityTextEditorProps {
  state: InfinityTextEditorState;
  scaleRef: React.RefObject<number>;
  stagePosRef: React.RefObject<{ x: number; y: number }>;
  // textEditor가 떠 있는 동안 stage 이벤트 차단을 위해 부모에서 toolSnapshot 사용 가능.
  // 여기서는 editor 자체의 commit/cancel만 책임.
  onCommit: (value: InfinityTextEditorCommitValue) => void;
  onCancel: () => void;
  // 사용자가 도구를 바꾸면 자동 commit (현재 도구 변경 추적은 부모가 closeTextEditor 호출).
  // editingTool: 현재 도구 — text가 아니면 자동 commit.
  editingTool: InfinityToolKey;
}

const TEXT_COLOR_OPTIONS = [INFINITY_TEXT_DEFAULT_COLOR, ...INFINITY_COLORS] as const;
const HEX_COLOR_PATTERN = /^#[0-9a-fA-F]{6}$/;
const TEXT_EDITOR_TOOLBAR_WIDTH = 460;
const TEXT_EDITOR_INPUT_WIDTH = 420;
const TEXT_EDITOR_MARGIN = 16;
const TEXT_EDITOR_TOOLBAR_OFFSET = 58;
const TEXT_EDITOR_INPUT_MIN_HEIGHT = 44;

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
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

function createViewportSnapshot(
  scaleRef: React.RefObject<number>,
  stagePosRef: React.RefObject<{ x: number; y: number }>,
) {
  const stagePosition = stagePosRef.current;
  return {
    scale: scaleRef.current,
    stagePosition: {
      x: stagePosition.x,
      y: stagePosition.y,
    },
  };
}

function createViewportSize() {
  if (typeof window === "undefined") {
    return {
      width: 1024,
      height: 768,
    };
  }

  return {
    width: window.innerWidth,
    height: window.innerHeight,
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
  const [textColor, setTextColor] = useState<string>(
    state.color || INFINITY_TEXT_DEFAULT_COLOR,
  );
  const [fontFamily, setFontFamily] = useState<string>(state.fontFamily);
  const [viewportSize, setViewportSize] = useState(createViewportSize);
  const editorRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const viewportSnapshot = createViewportSnapshot(scaleRef, stagePosRef);

  const screen = worldToScreen(
    { x: state.x, y: state.y },
    viewportSnapshot.scale,
    viewportSnapshot.stagePosition,
  );

  useEffect(() => {
    const updateViewportSize = () => {
      setViewportSize(createViewportSize());
    };

    window.addEventListener("resize", updateViewportSize);
    return () => window.removeEventListener("resize", updateViewportSize);
  }, []);

  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.focus();
    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
  }, []);

  const commitText = () => {
    onCommit({ text, fontSize, color: textColor, fontFamily });
  };

  // 도구가 text가 아닌 다른 도구로 바뀌면 자동 commit.
  useEffect(() => {
    if (editingTool !== "text" && editingTool !== "select") {
      commitText();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editingTool]);

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Escape") {
      event.preventDefault();
      onCancel();
    }
    // Shift+Enter는 줄바꿈, Enter 단독은 commit.
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      commitText();
    }
  };

  const handleEditorBlur = (event: React.FocusEvent<HTMLDivElement>) => {
    const nextFocusedNode = event.relatedTarget;
    if (
      nextFocusedNode instanceof Node &&
      editorRef.current?.contains(nextFocusedNode)
    ) {
      return;
    }
    commitText();
  };

  const adjustedFontSize = fontSize * viewportSnapshot.scale;
  const colorInputValue = HEX_COLOR_PATTERN.test(textColor)
    ? textColor
    : INFINITY_TEXT_DEFAULT_COLOR;
  const availableEditorWidth = Math.max(
    1,
    viewportSize.width - TEXT_EDITOR_MARGIN * 2,
  );
  const toolbarWidth = Math.min(TEXT_EDITOR_TOOLBAR_WIDTH, availableEditorWidth);
  const inputWidth = Math.min(TEXT_EDITOR_INPUT_WIDTH, availableEditorWidth);
  const anchorLeft = clamp(
    screen.x,
    TEXT_EDITOR_MARGIN,
    Math.max(TEXT_EDITOR_MARGIN, viewportSize.width - TEXT_EDITOR_MARGIN),
  );
  const toolbarLeftOffset = clamp(
    0,
    TEXT_EDITOR_MARGIN - anchorLeft,
    viewportSize.width - TEXT_EDITOR_MARGIN - anchorLeft - toolbarWidth,
  );
  const inputLeftOffset = clamp(
    0,
    TEXT_EDITOR_MARGIN - anchorLeft,
    viewportSize.width - TEXT_EDITOR_MARGIN - anchorLeft - inputWidth,
  );
  const minEditorTop = TEXT_EDITOR_MARGIN + TEXT_EDITOR_TOOLBAR_OFFSET;
  const maxEditorTop = Math.max(
    minEditorTop,
    viewportSize.height - TEXT_EDITOR_INPUT_MIN_HEIGHT - TEXT_EDITOR_MARGIN,
  );
  const anchorTop = clamp(screen.y, minEditorTop, maxEditorTop);

  return (
    <div
      ref={editorRef}
      className="absolute z-[var(--z-modal)] pointer-events-auto"
      style={{ left: anchorLeft, top: anchorTop }}
      onBlur={handleEditorBlur}
    >
      {/* 텍스트 서식 popover — textarea 위쪽 */}
      <div
        className="absolute bottom-full left-0 mb-2 flex flex-col gap-2 overflow-x-auto rounded-lg border border-canvas-border bg-white px-3 py-2 shadow-[0_12px_28px_rgba(35,64,140,0.18)]"
        style={{ left: toolbarLeftOffset, width: toolbarWidth }}
      >
        <div className="flex flex-nowrap items-center gap-1">
        <button
          type="button"
          onClick={() => setFontSize((size) => Math.max(8, size - 2))}
          className="w-6 h-6 flex items-center justify-center rounded hover:bg-canvas-active text-canvas-ink"
          aria-label="글자 크기 줄이기"
        >
          −
        </button>
        <input
          type="number"
          value={fontSize}
          min={8}
          max={256}
          onChange={(event) => {
            const nextFontSize = Number(event.target.value);
            if (!Number.isNaN(nextFontSize)) {
              setFontSize(Math.max(8, Math.min(256, nextFontSize)));
            }
          }}
          className="w-12 text-center body-r text-fg-primary bg-transparent outline-none"
          aria-label="글자 크기"
        />
        <button
          type="button"
          onClick={() => setFontSize((size) => Math.min(256, size + 2))}
          className="w-6 h-6 flex items-center justify-center rounded hover:bg-canvas-active text-canvas-ink"
          aria-label="글자 크기 키우기"
        >
          +
        </button>
        <div className="w-px h-4 bg-canvas-border mx-1" />
        <div className="flex flex-nowrap items-center gap-1">
          {INFINITY_TEXT_FONT_SIZES.map((fontSizeOption) => (
            <button
              key={fontSizeOption}
              type="button"
              onClick={() => setFontSize(fontSizeOption)}
              className={cn(
                "h-7 min-w-8 rounded px-2 body-r text-canvas-ink hover:bg-canvas-active",
                fontSize === fontSizeOption &&
                  "border border-canvas-accent bg-white text-canvas-accent",
              )}
            >
              {fontSizeOption}
            </button>
          ))}
        </div>
        </div>
        <div className="flex flex-nowrap items-center gap-2">
        <select
          value={fontFamily}
          onChange={(event) => setFontFamily(event.target.value)}
          className="h-8 w-24 shrink-0 rounded border border-canvas-border bg-white px-2 caption-r text-canvas-ink outline-none"
          aria-label="글꼴"
        >
          {INFINITY_TEXT_FONT_FAMILIES.map((fontOption) => (
            <option key={fontOption.value} value={fontOption.value}>
              {fontOption.label}
            </option>
          ))}
        </select>
        <div className="flex flex-nowrap items-center gap-1">
        {TEXT_COLOR_OPTIONS.map((colorOption) => (
          <button
            key={colorOption}
            type="button"
            onClick={() => setTextColor(colorOption)}
            className={cn(
              "h-7 w-7 shrink-0 rounded-full border border-canvas-border",
              textColor === colorOption && "ring-2 ring-canvas-accent ring-offset-1",
            )}
            style={{ backgroundColor: colorOption }}
            aria-label={`글자 색 ${colorOption}`}
          />
        ))}
        <input
          type="color"
          value={colorInputValue}
          onChange={(event) => setTextColor(event.target.value)}
          className="h-8 w-10 shrink-0 cursor-pointer rounded border border-canvas-border bg-white p-0.5"
          aria-label="사용자 지정 글자 색"
        />
          </div>
        </div>
      </div>

      {/* 입력 영역 */}
      <textarea
        ref={textareaRef}
        value={text}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={handleKeyDown}
        rows={1}
        spellCheck={false}
        className="resize-none rounded border border-canvas-accent bg-white px-1 py-0.5 leading-tight shadow-[0_8px_20px_rgba(35,64,140,0.12)] outline-none"
        style={{
          fontSize: `${adjustedFontSize}px`,
          color: textColor,
          left: inputLeftOffset,
          position: "relative",
          width: inputWidth,
          minWidth: "2ch",
          fontFamily,
        }}
      />
    </div>
  );
}
