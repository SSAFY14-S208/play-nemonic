'use client'

import {
  Brush,
  Check,
  Droplets,
  Eye,
  EyeOff,
  Palette,
  Timer,
} from 'lucide-react'
import { DRAWING_STROKE_WIDTH_OPTIONS } from '@/shared/constants'
import { cn } from '@/shared/libs'
import type { DrawingToolKey } from '@/shared/types'

type VisibleDrawingToolKey = Extract<DrawingToolKey, 'pencil' | 'eraser' | 'bucket'>
type ToolItemKey = VisibleDrawingToolKey | 'clear' | 'undo' | 'redo'

const DRAWING_TOOL_ICONS: Record<ToolItemKey, string> = {
  pencil: '/images/flipbook-drawing/figma-tools/tool-brush.svg',
  eraser: '/images/flipbook-drawing/figma-tools/tool-eraser.svg',
  bucket: '/images/flipbook-drawing/figma-tools/tool-bucket.svg',
  clear: '/images/flipbook-drawing/figma-tools/tool-trash.svg',
  undo: '/images/flipbook-drawing/figma-tools/tool-undo.svg',
  redo: '/images/flipbook-drawing/figma-tools/tool-redo.svg',
}

const TOOL_ITEMS: {
  key: ToolItemKey
  label: string
}[] = [
  { key: 'pencil', label: '브러시' },
  { key: 'eraser', label: '지우개' },
  { key: 'bucket', label: '채우기' },
  { key: 'undo', label: '실행 취소' },
  { key: 'redo', label: '다시 실행' },
  { key: 'clear', label: '전체 지우기' },
]

const RECENT_COLOR_SLOT_COUNT = 5

interface DrawingToolControlProps {
  selectedToolKey: DrawingToolKey
  canUndoDrawing: boolean
  canRedoDrawing: boolean
  onSelectTool: (toolKey: DrawingToolKey) => void
  onUndoDrawing: () => void
  onRedoDrawing: () => void
  onClearDrawing: () => void
}

interface DrawingColorControlProps {
  colors: string[]
  selectedColor: string
  selectedOpacity: number
  strokeWidth: number
  strokeWidthOptions?: number[]
  onSelectColor: (color: string) => void
  onOpacityChange: (opacity: number) => void
  onStrokeWidthChange: (strokeWidth: number) => void
}

export function MobileToolGrid({
  selectedToolKey,
  canUndoDrawing,
  canRedoDrawing,
  isDrawingLocked,
  onSelectTool,
  onUndoDrawing,
  onRedoDrawing,
  onClearDrawing,
}: DrawingToolControlProps & {
  isDrawingLocked: boolean
}) {
  return (
    <section
      className={cn(
        'rounded-[18px] border border-[#ead7c9] bg-white/90 p-3 shadow-[0_10px_24px_rgb(129_89_54_/_14%)]',
        isDrawingLocked && 'pointer-events-none opacity-60',
      )}
    >
      <p className="body-b mb-3 text-[#30343b]">도구</p>
      <div className="grid grid-cols-3 gap-2">
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
    </section>
  )
}

export function ToolPanel({
  className,
  selectedToolKey,
  canUndoDrawing,
  canRedoDrawing,
  onSelectTool,
  onUndoDrawing,
  onRedoDrawing,
  onClearDrawing,
}: DrawingToolControlProps & {
  className?: string
}) {
  return (
    <aside
      className={cn(
        'absolute left-[1264px] top-[358px] w-[204px] rounded-[31px] bg-white px-[18px] py-[26px] shadow-[0_8px_12px_rgb(0_0_0_/_18%)]',
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

export function MobileColorGrid({
  colors,
  selectedColor,
  selectedOpacity,
  strokeWidth,
  strokeWidthOptions = DRAWING_STROKE_WIDTH_OPTIONS,
  isDrawingLocked,
  onSelectColor,
  onOpacityChange,
  onStrokeWidthChange,
}: DrawingColorControlProps & {
  isDrawingLocked: boolean
}) {
  return (
    <section
      className={cn(
        'rounded-[18px] border border-[#ead7c9] bg-white/90 p-3 shadow-[0_10px_24px_rgb(129_89_54_/_14%)]',
        isDrawingLocked && 'pointer-events-none opacity-60',
      )}
    >
      <p className="body-b mb-3 text-[#30343b]">컬러</p>
      <div className="grid grid-cols-5 gap-2">
        {colors.slice(0, 20).map((color) => (
          <ColorSwatch
            key={color}
            color={color}
            selected={selectedColor === color}
            shape="square"
            onSelectColor={onSelectColor}
          />
        ))}
      </div>
      <StrokeWidthPicker
        className="mt-4 flex items-center gap-2"
        selectedStrokeWidth={strokeWidth}
        strokeWidthOptions={strokeWidthOptions}
        onStrokeWidthChange={onStrokeWidthChange}
      />
      <OpacitySlider
        className="mt-4"
        selectedOpacity={selectedOpacity}
        onOpacityChange={onOpacityChange}
      />
    </section>
  )
}

export function ColorPanel({
  className,
  colors,
  selectedColor,
  selectedOpacity,
  strokeWidth,
  recentColors = [],
  strokeWidthOptions = DRAWING_STROKE_WIDTH_OPTIONS,
  onSelectColor,
  onOpacityChange,
  onStrokeWidthChange,
}: DrawingColorControlProps & {
  className?: string
  recentColors?: string[]
}) {
  const recentColorSlots = Array.from(
    { length: RECENT_COLOR_SLOT_COUNT },
    (unusedValue, recentColorIndex) => recentColors[recentColorIndex] ?? null,
  )

  return (
    <aside
      className={cn(
        'absolute left-[36px] top-[230px] h-[646px] w-[272px] rounded-[28px] border border-white bg-white px-[25px] py-8 shadow-[2px_4px_10px_2px_#ede0d4]',
        className,
      )}
    >
      <p className="body-b flex items-center gap-2 text-[#30343b]">
        <Palette className="size-5" aria-hidden />
        컬러
      </p>

      <div className="mt-5 grid grid-cols-4 gap-x-[19px] gap-y-[15px]">
        {colors.slice(0, 20).map((color) => (
          <ColorSwatch
            key={color}
            color={color}
            selected={selectedColor === color}
            shape="square"
            onSelectColor={onSelectColor}
          />
        ))}
      </div>

      <p className="body-b mt-[18px] flex items-center gap-2 text-[#30343b]">
        <Brush className="size-5" aria-hidden />
        브러시 크기
      </p>
      <StrokeWidthPicker
        className="mt-3 flex items-center justify-between"
        selectedStrokeWidth={strokeWidth}
        strokeWidthOptions={strokeWidthOptions}
        onStrokeWidthChange={onStrokeWidthChange}
      />

      <p className="body-b mt-[18px] flex items-center gap-2 text-[#30343b]">
        <Droplets className="size-5" aria-hidden />
        투명도
      </p>
      <OpacitySlider
        className="mt-3"
        selectedOpacity={selectedOpacity}
        onOpacityChange={onOpacityChange}
      />

      <p className="body-b mt-5 flex items-center gap-2 text-[#30343b]">
        <Timer className="size-5" aria-hidden />
        최근 색상
      </p>
      <div className="mt-4 flex items-center justify-between">
        {recentColorSlots.map((color, recentColorIndex) =>
          color ? (
            <ColorSwatch
              key={`${color}-${recentColorIndex}`}
              color={color}
              selected={selectedColor === color}
              shape="circle"
              onSelectColor={onSelectColor}
              label={`${color} 최근 색상`}
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

export function TopStatusBar({
  activeRoundIndex,
  roundCount,
  remainingSeconds,
  remainingTimeLabel,
  timerUnitLabel = '초',
  instructionText,
}: {
  activeRoundIndex: number
  roundCount: number
  remainingSeconds: number
  remainingTimeLabel?: string
  timerUnitLabel?: string
  instructionText: string
}) {
  return (
    <header className="absolute left-[96px] top-[56px] h-[108px] w-[1344px] rounded-full border border-[#ead7c9] bg-white shadow-[0_12px_34px_rgb(129_89_54_/_14%)]">
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
          <span className="text-[34px] font-bold leading-none">
            {remainingTimeLabel ?? remainingSeconds}
          </span>
          {timerUnitLabel && <span className="body-l-b">{timerUnitLabel}</span>}
        </div>
      </div>
    </header>
  )
}

export function ProgressRail({
  activeRoundIndex,
  roundCount,
}: {
  activeRoundIndex: number
  roundCount: number
}) {
  const progressDotCount = Math.max(roundCount, 1)

  return (
    <div className="absolute left-[546px] top-[928px] h-[72px] w-[444px] rounded-full border border-[#ead7c9] bg-white shadow-[0_10px_22px_rgb(125_84_50_/_13%)]">
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

export function DrawingCompleteButton({
  className,
  disabled,
  label,
  onComplete,
}: {
  className?: string
  disabled: boolean
  label: string
  onComplete: () => void
}) {
  return (
    <button
      type="button"
      onClick={onComplete}
      disabled={disabled}
      className={cn(
        'body-l-b inline-flex items-center justify-center gap-3 rounded-[14px] bg-[#ff4f93] text-white shadow-[0_12px_24px_rgb(173_68_96_/_28%)]',
        disabled && 'cursor-not-allowed opacity-70',
        className,
      )}
    >
      <span className="grid size-8 place-items-center rounded-full bg-white">
        <Check className="size-5 text-[#ff4f93]" aria-hidden />
      </span>
      {label}
    </button>
  )
}

export function HintToggleButton({
  className,
  hasOnionSkinHint,
  isOnionSkinVisible,
  onToggle,
}: {
  className?: string
  hasOnionSkinHint: boolean
  isOnionSkinVisible: boolean
  onToggle: () => void
}) {
  const label = hasOnionSkinHint ? (isOnionSkinVisible ? '힌트 끄기' : '힌트 보기') : '힌트 없음'

  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={!hasOnionSkinHint}
      aria-label={label}
      aria-pressed={hasOnionSkinHint ? isOnionSkinVisible : undefined}
      title={label}
      className={cn(
        'body-b inline-flex h-[62px] w-[204px] items-center justify-center gap-3 rounded-[14px] border shadow-[0_8px_18px_rgb(129_89_54_/_13%)] transition',
        hasOnionSkinHint
          ? isOnionSkinVisible
            ? 'border-[#ff8bab] bg-[#ffecf3] text-[#db4d82]'
            : 'border-[#ead7c9] bg-white text-[#7d6251]'
          : 'cursor-not-allowed border-[#ead7c9] bg-[#f7efe7] text-[#b9a799]',
        className,
      )}
    >
      {isOnionSkinVisible && hasOnionSkinHint ? (
        <Eye className="size-5" aria-hidden />
      ) : (
        <EyeOff className="size-5" aria-hidden />
      )}
      {label}
    </button>
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
}: DrawingToolControlProps & {
  tool: { key: ToolItemKey; label: string }
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
          maskImage: `url(${DRAWING_TOOL_ICONS[tool.key]})`,
          maskPosition: 'center',
          maskRepeat: 'no-repeat',
          maskSize: 'contain',
          WebkitMaskImage: `url(${DRAWING_TOOL_ICONS[tool.key]})`,
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

function StrokeWidthPicker({
  className,
  selectedStrokeWidth,
  strokeWidthOptions,
  onStrokeWidthChange,
}: {
  className?: string
  selectedStrokeWidth: number
  strokeWidthOptions: number[]
  onStrokeWidthChange: (strokeWidth: number) => void
}) {
  return (
    <div className={className}>
      {strokeWidthOptions.map((strokeWidthOption) => (
        <button
          key={strokeWidthOption}
          type="button"
          aria-label={`${strokeWidthOption}px 굵기`}
          onClick={() => onStrokeWidthChange(strokeWidthOption)}
          className={cn(
            'grid size-7 place-items-center rounded-full border-2 border-[#403347] bg-white',
            selectedStrokeWidth === strokeWidthOption && 'border-[#ff4f8b]',
          )}
        >
          <span
            className="rounded-full bg-[#30343b]"
            style={{ width: strokeWidthOption, height: strokeWidthOption }}
          />
        </button>
      ))}
    </div>
  )
}

function OpacitySlider({
  className,
  selectedOpacity,
  onOpacityChange,
}: {
  className?: string
  selectedOpacity: number
  onOpacityChange: (opacity: number) => void
}) {
  return (
    <div className={cn('h-5', className)}>
      <input
        type="range"
        min={10}
        max={100}
        step={5}
        value={Math.round(selectedOpacity * 100)}
        aria-label={`투명도 ${Math.round(selectedOpacity * 100)}%`}
        onChange={(event) => onOpacityChange(Number(event.target.value) / 100)}
        className="h-4 w-full cursor-pointer appearance-none rounded-full border border-[#403347] bg-[linear-gradient(90deg,#ffffff_0%,#d9d9d9_45%,#212121_100%)] [&::-webkit-slider-runnable-track]:h-4 [&::-webkit-slider-runnable-track]:rounded-full [&::-webkit-slider-thumb]:mt-[-1px] [&::-webkit-slider-thumb]:size-[18px] [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-[#403347] [&::-webkit-slider-thumb]:bg-white"
      />
    </div>
  )
}

function ColorSwatch({
  color,
  selected,
  shape,
  label = `${color} 색상`,
  onSelectColor,
}: {
  color: string
  selected: boolean
  shape: 'circle' | 'square'
  label?: string
  onSelectColor: (color: string) => void
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={() => onSelectColor(color)}
      className={cn(
        'size-10 border border-[#d9d9de]',
        shape === 'circle' ? 'rounded-full' : 'rounded-[8px]',
        selected && 'ring-[3px] ring-[#f45d8d] ring-offset-2 ring-offset-white',
      )}
      style={{ backgroundColor: color }}
    />
  )
}
