'use client'

import { Brush, Droplets, Palette, Timer } from 'lucide-react'
import { cn } from '@/shared/libs'

const FLIPBOOK_STROKE_WIDTH_OPTIONS = [3, 6, 9, 12, 16]
const FLIPBOOK_RECENT_COLOR_SLOT_COUNT = 5

interface DrawingColorControlProps {
  colors: string[]
  selectedColor: string
  selectedOpacity: number
  strokeWidth: number
  onSelectColor: (color: string) => void
  onOpacityChange: (opacity: number) => void
  onStrokeWidthChange: (strokeWidth: number) => void
}

export function MobileColorGrid({
  colors,
  selectedColor,
  selectedOpacity,
  strokeWidth,
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
  recentColors,
  onSelectColor,
  onOpacityChange,
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
        'absolute left-[117px] top-[155px] h-[646px] w-[272px] rounded-[28px] border border-white bg-white px-[25px] py-8 shadow-[2px_4px_10px_2px_#ede0d4]',
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
      {FLIPBOOK_STROKE_WIDTH_OPTIONS.map((strokeWidthOption) => (
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
