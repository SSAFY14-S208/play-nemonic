'use client'

import dynamic from 'next/dynamic'
import Image from 'next/image'
import { useEffect, useState } from 'react'
import {
  Brush,
  Check,
  Palette,
  Timer,
} from 'lucide-react'
import { cn } from '@/shared/libs'
import type {
  DrawingLine,
  DrawingPointerEvent,
  DrawingToolKey,
  FlipbookConnectionStatus,
} from '@/shared/types'
import { FLIPBOOK_COLORS, type FlipbookParticipant } from '../constants'
import type { FlipbookDrawingSubmissionState } from '../types'

const FlipbookStage = dynamic(() => import('../FlipbookStage'), {
  ssr: false,
})

const FLIPBOOK_STROKE_WIDTH_OPTIONS = [3, 6, 9, 12, 16]
const FLIPBOOK_RECENT_COLOR_SLOT_COUNT = 5
const FLIPBOOK_DRAWING_IMAGES = {
  background: '/images/flipbook-lobby/background.png',
}
const FLIPBOOK_TOOL_ICONS: Record<ToolItemKey, string> = {
  pencil: '/images/flipbook-drawing/figma-tools/tool-brush.svg',
  eraser: '/images/flipbook-drawing/figma-tools/tool-eraser.svg',
  bucket: '/images/flipbook-drawing/figma-tools/tool-bucket.svg',
  clear: '/images/flipbook-drawing/figma-tools/tool-trash.svg',
  undo: '/images/flipbook-drawing/figma-tools/tool-undo.svg',
  redo: '/images/flipbook-drawing/figma-tools/tool-redo.svg',
}

type FigmaDrawingToolKey = Extract<DrawingToolKey, 'pencil' | 'eraser' | 'bucket'>
type ToolItemKey = FigmaDrawingToolKey | 'clear' | 'undo' | 'redo'
const TOOL_ITEMS: {
  key: ToolItemKey
  label: string
}[] = [
  { key: 'pencil', label: '브러시' },
  { key: 'eraser', label: '지우개' },
  { key: 'bucket', label: '채우기' },
  { key: 'clear', label: '전체 지우기' },
  { key: 'undo', label: '실행 취소' },
  { key: 'redo', label: '다시 실행' },
]

interface FlipbookDrawingViewProps {
  activeRoundIndex: number
  roundCount: number
  remainingSeconds: number
  currentParticipant: FlipbookParticipant
  isSubmitting: boolean
  isRoundSubmitted: boolean
  connectionStatus: FlipbookConnectionStatus
  errorMessage: string | null
  lines: DrawingLine[]
  previousFrameLines: DrawingLine[]
  selectedToolKey: DrawingToolKey
  selectedColor: string
  strokeWidth: number
  recentColors: string[]
  canUndoDrawing: boolean
  canRedoDrawing: boolean
  onSelectTool: (toolKey: DrawingToolKey) => void
  onSelectColor: (color: string) => void
  onStrokeWidthChange: (strokeWidth: number) => void
  onUndoDrawing: () => void
  onRedoDrawing: () => void
  onClearDrawing: () => void
  onDrawStart: (event: DrawingPointerEvent) => void
  onDrawMove: (event: DrawingPointerEvent) => void
  onDrawEnd: () => void
  onExit: () => void
  onCompleteRound: () => void | Promise<void>
}

export default function FlipbookDrawingView({
  activeRoundIndex,
  roundCount,
  remainingSeconds,
  currentParticipant,
  isSubmitting,
  isRoundSubmitted,
  connectionStatus,
  errorMessage,
  lines,
  previousFrameLines,
  selectedToolKey,
  selectedColor,
  strokeWidth,
  recentColors,
  canUndoDrawing,
  canRedoDrawing,
  onSelectTool,
  onSelectColor,
  onStrokeWidthChange,
  onUndoDrawing,
  onRedoDrawing,
  onClearDrawing,
  onDrawStart,
  onDrawMove,
  onDrawEnd,
  onCompleteRound,
}: FlipbookDrawingViewProps) {
  const [submittedRoundIndex, setSubmittedRoundIndex] = useState<number | null>(null)
  const [isOnionSkinVisible, setIsOnionSkinVisible] = useState(true)
  const isConnectionUnstable =
    connectionStatus === 'reconnecting' || connectionStatus === 'disconnected'
  const isWaitingForNextRound =
    (isRoundSubmitted || submittedRoundIndex === activeRoundIndex) && !isSubmitting
  const drawingSubmissionState: FlipbookDrawingSubmissionState = isSubmitting
    ? 'submitting'
    : isWaitingForNextRound
      ? 'waiting'
      : 'drawing'
  const isDrawingLocked = isConnectionUnstable || drawingSubmissionState !== 'drawing'
  const hasOnionSkinHint = previousFrameLines.length > 0
  const instructionText =
    activeRoundIndex === 0
      ? '첫 장면을 그려주세요'
      : hasOnionSkinHint && isOnionSkinVisible
        ? '연하게 보이는 이전 그림을 이어 그려주세요'
        : '다음 장면을 이어 그려주세요'
  const submitButtonText =
    drawingSubmissionState === 'submitting'
      ? '제출 중'
      : drawingSubmissionState === 'waiting'
        ? '대기 중'
        : '완료!'
  const overlayMessage =
    drawingSubmissionState === 'submitting'
      ? '그림을 제출하고 있어요'
      : drawingSubmissionState === 'waiting'
        ? '제출 완료! 다음 라운드를 기다리는 중이에요'
        : null

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (submittedRoundIndex === null) return

      const shouldUnlockDrawing =
        (submittedRoundIndex !== activeRoundIndex && !isRoundSubmitted) || errorMessage !== null
      if (!cancelled && shouldUnlockDrawing) {
        setSubmittedRoundIndex(null)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeRoundIndex, errorMessage, isRoundSubmitted, submittedRoundIndex])

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (!cancelled) {
        setIsOnionSkinVisible(true)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeRoundIndex, previousFrameLines.length])

  const handleCompleteRound = () => {
    if (isDrawingLocked) return

    setSubmittedRoundIndex(activeRoundIndex)
    void onCompleteRound()
  }

  return (
    <section
      className="relative grid h-screen place-items-center overflow-hidden bg-[#fdf1e6] text-[#30343b]"
      aria-label={`${currentParticipant.name} 플립북 드로잉`}
    >
      <Image
        src={FLIPBOOK_DRAWING_IMAGES.background}
        alt=""
        fill
        priority
        sizes="100vw"
        className="pointer-events-none object-cover"
        aria-hidden
      />

      <div className="relative h-[819.2px] w-[1228.8px] shrink-0">
        <div className="absolute left-0 top-0 h-[1024px] w-[1536px] origin-top-left scale-[0.8]">
          <TopStatusBar
            activeRoundIndex={activeRoundIndex}
            roundCount={roundCount}
            remainingSeconds={remainingSeconds}
            instructionText={instructionText}
          />

          <ColorPanel
            className={cn(isDrawingLocked && 'pointer-events-none opacity-60')}
            colors={FLIPBOOK_COLORS}
            selectedColor={selectedColor}
            strokeWidth={strokeWidth}
            recentColors={recentColors}
            onSelectColor={onSelectColor}
            onStrokeWidthChange={onStrokeWidthChange}
          />

          <main className="absolute left-[432px] top-[195px] h-[566px] w-[738px]">
            <div className="absolute inset-0 rounded-[8px] bg-white shadow-[0_8px_42px_-10px_rgb(0_0_0_/_25%)]" />
            <div className="absolute left-[29px] top-[23px] z-10 h-[520px] w-[680px] overflow-hidden rounded-[4px] bg-white">
              <FlipbookStage
                lines={lines}
                previousFrameLines={isOnionSkinVisible ? previousFrameLines : []}
                disabled={isDrawingLocked}
                onDrawStart={onDrawStart}
                onDrawMove={onDrawMove}
                onDrawEnd={onDrawEnd}
              />
              {overlayMessage && (
                <div className="absolute inset-0 grid place-items-center bg-[#fff4a7]/70 text-flipbook-deep">
                  <div className="rounded-[14px] bg-flipbook-paper/92 px-6 py-4 text-center shadow-[0_4px_12px_var(--color-flipbook-shadow)]">
                    <p className="body-l-b">{overlayMessage}</p>
                    <p className="caption-m mt-2 text-flipbook-deep/75">
                      캔버스는 잠시 잠겨 있어요
                    </p>
                  </div>
                </div>
              )}
              {isConnectionUnstable && (
                <div className="body-b absolute inset-0 grid place-items-center bg-[#fff4a7]/72 text-flipbook-deep">
                  연결 끊김 — 재연결 중...
                </div>
              )}
            </div>
          </main>

          <ToolPanel
            className={cn(isDrawingLocked && 'pointer-events-none opacity-60')}
            selectedToolKey={selectedToolKey}
            canUndoDrawing={canUndoDrawing}
            canRedoDrawing={canRedoDrawing}
            onSelectTool={onSelectTool}
            onUndoDrawing={onUndoDrawing}
            onRedoDrawing={onRedoDrawing}
            onClearDrawing={onClearDrawing}
          />

          <ProgressRail activeRoundIndex={activeRoundIndex} roundCount={roundCount} />

          <button
            type="button"
            onClick={handleCompleteRound}
            disabled={isDrawingLocked}
            className={cn(
              'body-l-b absolute left-[1228px] top-[700px] inline-flex h-[62px] w-[198px] items-center justify-center gap-3 rounded-[14px] bg-[#ff4f93] text-white shadow-[0_12px_24px_rgb(173_68_96_/_28%)]',
              isDrawingLocked && 'cursor-not-allowed opacity-70',
            )}
          >
            <span className="grid size-8 place-items-center rounded-full bg-white">
              <Check className="size-5 text-[#ff4f93]" aria-hidden />
            </span>
            {submitButtonText === '완료!' ? '완료하기' : submitButtonText}
          </button>
          {errorMessage && (
            <p className="caption-b absolute left-[432px] top-[782px] w-[738px] text-center text-flipbook-deep">
              {errorMessage}
            </p>
          )}
        </div>
      </div>
    </section>
  )
}

function TopStatusBar({
  activeRoundIndex,
  roundCount,
  remainingSeconds,
  instructionText,
}: {
  activeRoundIndex: number
  roundCount: number
  remainingSeconds: number
  instructionText: string
}) {
  return (
    <header className="absolute left-[96px] top-6 h-[108px] w-[1344px] rounded-full border border-[#ead7c9] bg-white shadow-[0_12px_34px_rgb(129_89_54_/_14%)]">
      <div className="flex h-full items-center px-[86px]">
        <p className="text-[74px] font-bold leading-none text-[#f45d8d]">
          {activeRoundIndex + 1}/{roundCount}
        </p>
        <div className="mx-10 h-14 w-px bg-[#ead7c9]" />
        <div>
          <p className="body-l-b text-[26px] text-[#30343b]">{instructionText}</p>
        </div>
        <div className="ml-auto inline-flex h-16 min-w-[184px] items-center justify-center gap-3 rounded-full border border-[#ead7c9] bg-white px-6 text-[#f45d8d] shadow-[0_7px_16px_rgb(129_89_54_/_13%)]">
          <Timer className="size-10" aria-hidden />
          <span className="text-[34px] font-bold leading-none">{remainingSeconds}</span>
          <span className="body-l-b">초</span>
        </div>
      </div>
    </header>
  )
}

function ColorPanel({
  className,
  colors,
  selectedColor,
  strokeWidth,
  recentColors,
  onSelectColor,
  onStrokeWidthChange,
}: {
  className?: string
  colors: string[]
  selectedColor: string
  strokeWidth: number
  recentColors: string[]
  onSelectColor: (color: string) => void
  onStrokeWidthChange: (strokeWidth: number) => void
}) {
  const recentColorSlots = Array.from(
    { length: FLIPBOOK_RECENT_COLOR_SLOT_COUNT },
    (unusedValue, recentColorIndex) => recentColors[recentColorIndex] ?? null,
  )

  return (
    <aside
      className={cn(
        'absolute left-[117px] top-[185px] h-[604px] w-[272px] rounded-[24px] border border-[#ead7c9] bg-white/88 px-7 py-8 shadow-[0_14px_32px_rgb(129_89_54_/_15%)] backdrop-blur-sm',
        className,
      )}
    >
      <p className="body-b flex items-center gap-2 text-[#30343b]">
        <Palette className="size-5" aria-hidden />
        컬러
      </p>

      <div className="mt-5 grid grid-cols-4 gap-4">
        {colors.slice(0, 20).map((color) => (
          <button
            key={color}
            type="button"
            aria-label={`${color} 색상`}
            onClick={() => onSelectColor(color)}
            className={cn(
              'size-9 rounded-[8px] border border-[#ead7c9] shadow-[inset_0_0_8px_rgb(255_255_255_/_28%)]',
              selectedColor === color && 'ring-[3px] ring-[#f45d8d] ring-offset-2 ring-offset-white',
            )}
            style={{ backgroundColor: color }}
          />
        ))}
      </div>

      <div className="my-5 h-px bg-[#ead7c9]" />

      <p className="body-b flex items-center gap-2 text-[#30343b]">
        <Brush className="size-5" aria-hidden />
        브러시 크기
      </p>
      <div className="mt-4 flex items-center justify-between">
        {FLIPBOOK_STROKE_WIDTH_OPTIONS.map((strokeWidthOption) => (
          <button
            key={strokeWidthOption}
            type="button"
            aria-label={`${strokeWidthOption}px 굵기`}
            onClick={() => onStrokeWidthChange(strokeWidthOption)}
            className={cn(
              'grid size-8 place-items-center rounded-full bg-[#f7efe7]',
              strokeWidth === strokeWidthOption && 'bg-[#ffd4df]',
            )}
          >
            <span
              className="rounded-full bg-[#30343b]"
              style={{
                width: strokeWidthOption,
                height: strokeWidthOption,
              }}
            />
          </button>
        ))}
      </div>

      <p className="body-b mt-7 flex items-center gap-2 text-[#30343b]">
        <Timer className="size-5" aria-hidden />
        최근 색상
      </p>
      <div className="mt-4 flex items-center justify-between">
        {recentColorSlots.map((color, recentColorIndex) =>
          color ? (
            <button
              key={`${color}-${recentColorIndex}`}
              type="button"
              aria-label={`${color} 최근 색상`}
              onClick={() => onSelectColor(color)}
              className={cn(
                'size-9 rounded-full border border-[#ead7c9]',
                selectedColor === color && 'ring-[3px] ring-[#f45d8d] ring-offset-2 ring-offset-white',
              )}
              style={{ backgroundColor: color }}
            />
          ) : (
            <span
              key={`empty-recent-color-${recentColorIndex}`}
              aria-hidden
              className="size-9 rounded-full border border-dashed border-[#ead7c9] bg-[#fbf4ee]"
            />
          ),
        )}
      </div>
    </aside>
  )
}

function ToolPanel({
  className,
  selectedToolKey,
  canUndoDrawing,
  canRedoDrawing,
  onSelectTool,
  onUndoDrawing,
  onRedoDrawing,
  onClearDrawing,
}: {
  className?: string
  selectedToolKey: DrawingToolKey
  canUndoDrawing: boolean
  canRedoDrawing: boolean
  onSelectTool: (toolKey: DrawingToolKey) => void
  onUndoDrawing: () => void
  onRedoDrawing: () => void
  onClearDrawing: () => void
}) {
  return (
    <aside
      className={cn(
        'absolute left-[1222px] top-[206px] w-[204px] rounded-[31px] bg-white px-[18px] py-[26px] shadow-[0_8px_12px_rgb(0_0_0_/_18%)]',
        className,
      )}
    >
      <div className="grid gap-2">
        {TOOL_ITEMS.map((tool) => (
          <ToolPanelButton
            key={tool.key}
            tool={tool}
            selectedToolKey={selectedToolKey}
            canUndoDrawing={canUndoDrawing}
            canRedoDrawing={canRedoDrawing}
            onSelectTool={onSelectTool}
            onUndoDrawing={onUndoDrawing}
            onRedoDrawing={onRedoDrawing}
            onClearDrawing={onClearDrawing}
          />
        ))}
      </div>
    </aside>
  )
}

function ToolPanelButton({
  tool,
  selectedToolKey,
  canUndoDrawing,
  canRedoDrawing,
  onSelectTool,
  onUndoDrawing,
  onRedoDrawing,
  onClearDrawing,
}: {
  tool: { key: ToolItemKey; label: string }
  selectedToolKey: DrawingToolKey
  canUndoDrawing: boolean
  canRedoDrawing: boolean
  onSelectTool: (toolKey: DrawingToolKey) => void
  onUndoDrawing: () => void
  onRedoDrawing: () => void
  onClearDrawing: () => void
}) {
  const isSelectedDrawingTool = selectedToolKey === tool.key
  const isHistoryCommandDisabled =
    (tool.key === 'undo' && !canUndoDrawing) || (tool.key === 'redo' && !canRedoDrawing)

  const handleClick = () => {
    if (tool.key === 'clear') {
      onClearDrawing()
      return
    }
    if (tool.key === 'undo') {
      if (canUndoDrawing) onUndoDrawing()
      return
    }
    if (tool.key === 'redo') {
      if (canRedoDrawing) onRedoDrawing()
      return
    }

    onSelectTool(tool.key)
  }

  return (
    <button
      type="button"
      disabled={isHistoryCommandDisabled}
      onClick={handleClick}
      className={cn(
        'flex h-[56px] w-full items-center gap-4 rounded-[16px] px-6 text-left text-[#1f1f1f] shadow-[0_4px_2px_rgb(0_0_0_/_5%)] transition',
        isSelectedDrawingTool && 'bg-[#feebef] text-[#dc6c92]',
        isHistoryCommandDisabled && 'cursor-not-allowed text-[#c5c5c5]',
      )}
    >
      <span
        className="size-6 bg-current"
        style={{
          maskImage: `url(${FLIPBOOK_TOOL_ICONS[tool.key]})`,
          maskPosition: 'center',
          maskRepeat: 'no-repeat',
          maskSize: 'contain',
          WebkitMaskImage: `url(${FLIPBOOK_TOOL_ICONS[tool.key]})`,
          WebkitMaskPosition: 'center',
          WebkitMaskRepeat: 'no-repeat',
          WebkitMaskSize: 'contain',
        }}
        aria-hidden
      />
      <span className="text-[16px] font-medium leading-none">{tool.label}</span>
    </button>
  )
}

function ProgressRail({
  activeRoundIndex,
  roundCount,
}: {
  activeRoundIndex: number
  roundCount: number
}) {
  const progressDotCount = Math.max(roundCount, 1)

  return (
    <div className="absolute left-[570px] top-[826px] h-[72px] w-[444px] rounded-full border border-[#ead7c9] bg-white shadow-[0_10px_22px_rgb(125_84_50_/_13%)]">
      <div className="absolute left-[64px] right-[64px] top-1/2 h-[3px] -translate-y-1/2 bg-[#ded3ca]" />
      {Array.from({ length: progressDotCount }).map((unusedValue, progressIndex) => (
        <span
          key={`${unusedValue}-${progressIndex}`}
          className={cn(
            'absolute top-1/2 grid size-6 -translate-y-1/2 place-items-center rounded-full border-[3px] border-[#cfc5bd] bg-white',
            progressIndex <= activeRoundIndex && 'border-[#f45d8d] bg-[#f45d8d]',
            progressIndex === activeRoundIndex && 'ring-[6px] ring-white',
          )}
          style={{ left: `${64 + progressIndex * (316 / Math.max(progressDotCount - 1, 1))}px` }}
        />
      ))}
    </div>
  )
}
