'use client'

import type { CSSProperties } from 'react'
import {
  ArrowLeft,
  Download,
  Eraser,
  PenLine,
  Printer,
  Redo2,
  Trash2,
  Undo2,
} from 'lucide-react'
import { cn } from '@/shared/libs'
import {
  PHONE_COLORS,
  PHONE_DRAWING_COLORS,
  PHONE_DRAWING_STAGE_SIZE,
  PHONE_MAX_BRUSH_SIZE,
  PHONE_MIN_BRUSH_SIZE,
} from '../constants'
import { usePhoneDrawing } from '../hooks'
import { PhoneDrawingStage } from './PhoneDrawingStage'

interface PhoneDrawingScreenProps {
  onBack: () => void
  onCreateArtifact: (imageDataUrl: string, action: 'save' | 'print') => void
}

export function PhoneDrawingScreen({
  onBack,
  onCreateArtifact,
}: PhoneDrawingScreenProps) {
  const {
    activeTool,
    brushSize,
    clearDrawing,
    createArtifact,
    draw,
    endDrawing,
    hasDrawing,
    lines,
    redoDrawing,
    redoLines,
    selectColor,
    selectedColor,
    setActiveTool,
    setBrushSize,
    stageRef,
    startDrawing,
    undoDrawing,
  } = usePhoneDrawing(onCreateArtifact)

  return (
    <div
      className="flex h-full flex-col pt-12 text-fg-primary"
      style={{ background: PHONE_COLORS.drawingBackground }}
    >
      <header
        className="flex h-14 items-center justify-between border-b border-border-default px-3"
        style={{ background: PHONE_COLORS.drawingPanel }}
      >
        <button
          type="button"
          aria-label="홈으로 돌아가기"
          onClick={onBack}
          className="flex h-11 w-11 items-center justify-center rounded-full transition hover:bg-white focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2"
          style={{ color: PHONE_COLORS.drawingAccent }}
        >
          <ArrowLeft className="h-6 w-6" />
        </button>
        <h2 className="h4-b">네모닉 그림판</h2>
        <div className="flex items-center gap-1">
          <button
            type="button"
            aria-label="되돌리기"
            disabled={lines.length === 0}
            onClick={undoDrawing}
            className="flex h-10 w-10 items-center justify-center rounded-[var(--radius-md)] text-fg-secondary transition hover:bg-white disabled:text-fg-disabled"
          >
            <Undo2 className="h-5 w-5" />
          </button>
          <button
            type="button"
            aria-label="다시 실행"
            disabled={redoLines.length === 0}
            onClick={redoDrawing}
            className="flex h-10 w-10 items-center justify-center rounded-[var(--radius-md)] text-fg-secondary transition hover:bg-white disabled:text-fg-disabled"
          >
            <Redo2 className="h-5 w-5" />
          </button>
        </div>
      </header>

      <div className="border-b border-border-default bg-white px-6 py-3">
        <div className="mb-3 flex items-center justify-center gap-10">
          <button
            type="button"
            aria-label="펜"
            onClick={() => setActiveTool('pen')}
            className={cn(
              'flex h-11 w-11 items-center justify-center rounded-full text-fg-secondary transition',
              activeTool === 'pen' && 'text-[var(--phone-drawing-accent)]',
            )}
            style={{
              '--phone-drawing-accent': PHONE_COLORS.drawingAccent,
              background:
                activeTool === 'pen'
                  ? PHONE_COLORS.drawingAccentMuted
                  : 'transparent',
            } as CSSProperties}
          >
            <PenLine className="h-7 w-7" />
          </button>
          <button
            type="button"
            aria-label="지우개"
            onClick={() => setActiveTool('eraser')}
            className={cn(
              'flex h-11 w-11 items-center justify-center rounded-full text-fg-secondary transition',
              activeTool === 'eraser' && 'bg-surface-subtle text-fg-primary',
            )}
          >
            <Eraser className="h-7 w-7" />
          </button>
          <button
            type="button"
            aria-label="전체 지우기"
            disabled={!hasDrawing}
            onClick={clearDrawing}
            className="flex h-11 w-11 items-center justify-center rounded-full text-fg-secondary transition hover:bg-white disabled:text-fg-disabled"
          >
            <Trash2 className="h-7 w-7" />
          </button>
        </div>

        <div className="flex items-center gap-3 rounded-[var(--radius-md)] bg-white px-4 py-3 shadow-[0_0.25rem_0.7rem_rgba(0,0,0,0.12)]">
          <span
            className="h-1 w-11 rounded-full"
            style={{ background: selectedColor }}
          />
          <input
            aria-label="브러시 굵기"
            type="range"
            min={PHONE_MIN_BRUSH_SIZE}
            max={PHONE_MAX_BRUSH_SIZE}
            step={1}
            value={brushSize}
            onChange={(event) => setBrushSize(Number(event.target.value))}
            className="h-1 flex-1"
            style={{ accentColor: PHONE_COLORS.drawingAccent }}
          />
        </div>
      </div>

      <main className="flex flex-1 flex-col gap-4 overflow-y-auto px-6 py-5">
        <div
          className="mx-auto w-full max-w-[327px] overflow-hidden border border-border-default bg-white shadow-[0.12rem_0.12rem_0.6rem_rgba(0,0,0,0.25)]"
          style={{ aspectRatio: `${PHONE_DRAWING_STAGE_SIZE.width} / ${PHONE_DRAWING_STAGE_SIZE.height}` }}
        >
          <PhoneDrawingStage
            lines={lines}
            onDrawEnd={endDrawing}
            onDrawMove={draw}
            onDrawStart={startDrawing}
            stageRef={stageRef}
          />
        </div>

        <div className="grid grid-cols-6 gap-2">
          {PHONE_DRAWING_COLORS.map((color) => (
            <button
              key={color}
              type="button"
              aria-label={`${color} 색상 선택`}
              onClick={() => selectColor(color)}
              className={cn(
                'h-8 rounded-full border-2 border-white shadow-[0_0.12rem_0.35rem_rgba(0,0,0,0.16)] transition hover:scale-105 focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2',
                selectedColor === color && activeTool === 'pen' && 'scale-110 border-fg-primary',
              )}
              style={{ background: color }}
            />
          ))}
        </div>
      </main>

      <footer
        className="grid grid-cols-2 gap-4 border-t border-border-default px-7 py-5 pb-8"
        style={{ background: PHONE_COLORS.drawingPanel }}
      >
        <button
          type="button"
          disabled={!hasDrawing}
          onClick={() => createArtifact('save')}
          className="body-l-b flex h-14 items-center justify-center gap-3 rounded-[var(--radius-md)] text-fg-primary shadow-[0_0.2rem_0.35rem_rgba(0,0,0,0.2)] transition hover:-translate-y-0.5 disabled:translate-y-0 disabled:bg-surface-subtle disabled:text-fg-disabled"
          style={{ background: hasDrawing ? PHONE_COLORS.saveButton : undefined }}
        >
          <Download className="h-6 w-6" />
          갤러리에 저장
        </button>
        <button
          type="button"
          disabled={!hasDrawing}
          onClick={() => createArtifact('print')}
          className="body-l-b flex h-14 items-center justify-center gap-3 rounded-[var(--radius-md)] text-fg-primary shadow-[0_0.2rem_0.35rem_rgba(0,0,0,0.2)] transition hover:-translate-y-0.5 disabled:translate-y-0 disabled:bg-surface-subtle disabled:text-fg-disabled"
          style={{ background: hasDrawing ? PHONE_COLORS.printButton : undefined }}
        >
          <Printer className="h-6 w-6" />
          네모닉 출력
        </button>
      </footer>
    </div>
  )
}
