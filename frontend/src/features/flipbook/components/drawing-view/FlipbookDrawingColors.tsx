'use client'

import { Brush, Palette, Timer } from 'lucide-react'
import { cn } from '@/shared/libs'

const FLIPBOOK_STROKE_WIDTH_OPTIONS = [3, 6, 9, 12, 16]
const FLIPBOOK_RECENT_COLOR_SLOT_COUNT = 5

interface DrawingColorControlProps {
  colors: string[]
  selectedColor: string
  strokeWidth: number
  onSelectColor: (color: string) => void
  onStrokeWidthChange: (strokeWidth: number) => void
}

export function MobileColorGrid({
  colors,
  selectedColor,
  strokeWidth,
  isDrawingLocked,
  onSelectColor,
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
      <p className="body-b mb-3 text-[#30343b]">색상</p>
      <div className="grid grid-cols-6 gap-2">
        {colors.slice(0, 18).map((color) => (
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
        onStrokeWidthChange={onStrokeWidthChange}
      />
    </section>
  )
}

export function ColorPanel({
  className,
  colors,
  selectedColor,
  strokeWidth,
  recentColors,
  onSelectColor,
  onStrokeWidthChange,
}: DrawingColorControlProps & {
  className?: string
  recentColors: string[]
}) {
  const recentColorSlots = Array.from(
    { length: FLIPBOOK_RECENT_COLOR_SLOT_COUNT },
    (unusedValue, recentColorIndex) => recentColors[recentColorIndex] ?? null,
  )

  return (
    <aside
      className={cn(
        'absolute left-[117px] top-[176px] h-[604px] w-[272px] rounded-[24px] border border-[#ead7c9] bg-white/88 px-7 py-8 shadow-[0_14px_32px_rgb(129_89_54_/_15%)] backdrop-blur-sm',
        className,
      )}
    >
      <p className="body-b flex items-center gap-2 text-[#30343b]">
        <Palette className="size-5" aria-hidden />
        컬러
      </p>

      <div className="mt-5 grid grid-cols-4 gap-4">
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

      <div className="my-5 h-px bg-[#ead7c9]" />

      <p className="body-b flex items-center gap-2 text-[#30343b]">
        <Brush className="size-5" aria-hidden />
        브러시 크기
      </p>
      <StrokeWidthPicker
        className="mt-4 flex items-center justify-between"
        selectedStrokeWidth={strokeWidth}
        onStrokeWidthChange={onStrokeWidthChange}
      />

      <p className="body-b mt-7 flex items-center gap-2 text-[#30343b]">
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

function StrokeWidthPicker({
  className,
  selectedStrokeWidth,
  onStrokeWidthChange,
}: {
  className?: string
  selectedStrokeWidth: number
  onStrokeWidthChange: (strokeWidth: number) => void
}) {
  return (
    <div className={className}>
      <span className="caption-b min-w-12 text-[#30343b]">굵기</span>
      {FLIPBOOK_STROKE_WIDTH_OPTIONS.map((strokeWidthOption) => (
        <button
          key={strokeWidthOption}
          type="button"
          aria-label={`${strokeWidthOption}px 굵기`}
          onClick={() => onStrokeWidthChange(strokeWidthOption)}
          className={cn(
            'grid size-8 place-items-center rounded-full bg-[#f7efe7]',
            selectedStrokeWidth === strokeWidthOption && 'bg-[#ffd4df]',
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
        'size-9 border border-[#ead7c9]',
        shape === 'circle' ? 'rounded-full' : 'rounded-[8px]',
        selected && 'ring-[3px] ring-[#f45d8d] ring-offset-2 ring-offset-white',
      )}
      style={{ backgroundColor: color }}
    />
  )
}
