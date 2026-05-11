'use client'

import {
  Eraser,
  PaintBucket,
  Pencil,
  Redo2,
  Trash2,
  Undo2,
  type LucideIcon,
} from 'lucide-react'
import { cn } from '@/shared/libs'
import type { DrawingToolKey } from '@/shared/types'

type DrawingCommandKey = DrawingToolKey | 'undo' | 'redo' | 'clear'
type DrawingPanelTone = 'relay' | 'flipbook'
type DrawingToolPanelSection = 'tools' | 'stroke' | 'colors'

interface DrawingToolButton {
  key: DrawingCommandKey
  label: string
  Icon: LucideIcon
  action: 'select' | 'undo' | 'redo' | 'clear'
}

interface DrawingToolPanelProps {
  selectedToolKey: DrawingToolKey
  selectedColor: string
  strokeWidth: number
  colors: string[]
  tone?: DrawingPanelTone
  sections?: DrawingToolPanelSection[]
  showSectionLabels?: boolean
  showSelectedColorPreview?: boolean
  strokeWidthOptions?: number[]
  className?: string
  onSelectTool: (toolKey: DrawingToolKey) => void
  onSelectColor: (color: string) => void
  onStrokeWidthChange: (strokeWidth: number) => void
  onUndoDrawing: () => void
  onRedoDrawing?: () => void
  onClearDrawing: () => void
}

const DRAWING_TOOL_BUTTONS: DrawingToolButton[] = [
  { key: 'pencil', label: '펜', Icon: Pencil, action: 'select' },
  { key: 'eraser', label: '지우개', Icon: Eraser, action: 'select' },
  { key: 'bucket', label: '채우기', Icon: PaintBucket, action: 'select' },
  { key: 'clear', label: '전체 지우기', Icon: Trash2, action: 'clear' },
  { key: 'undo', label: '되돌리기', Icon: Undo2, action: 'undo' },
  { key: 'redo', label: '다시 실행', Icon: Redo2, action: 'redo' },
]

const STROKE_WIDTH_OPTIONS = [3, 6, 10]
const DEFAULT_PANEL_SECTIONS: DrawingToolPanelSection[] = ['tools', 'stroke', 'colors']

const TONE_STYLES = {
  relay: {
    heading: 'text-relay-ink',
    panel: 'grid gap-6',
    toolGrid: 'grid w-[186px] grid-cols-3 gap-[15px]',
    button: 'border-relay-line bg-relay-active text-relay-ink',
    activeButton: 'border-relay-accent-strong bg-relay-accent',
    toolButton: 'grid size-[52px] place-items-center rounded-[14px] border',
    toolIcon: 'size-[18px]',
    strokeGrid: 'grid w-[186px] grid-cols-3 gap-[15px]',
    strokeButton: 'grid h-10 place-items-center rounded-full border',
    activeStrokeButton: 'border-relay-accent-strong bg-relay-accent',
    selectedColor: 'border-relay-accent-strong',
    neutralColor: 'border-relay-dash',
    colorGrid: 'grid w-[186px] grid-cols-4 gap-[14px]',
    colorButton: 'size-9 rounded-full border border-transparent',
    selectedColorPreview: 'hidden',
    strokeDot: 'bg-relay-dash',
    activeStrokeDot: 'bg-relay-dash',
  },
  flipbook: {
    heading: 'text-flipbook-ink',
    panel: 'grid gap-0',
    toolGrid: 'grid w-[144px] grid-cols-2 gap-[13px]',
    button: 'bg-white text-[#1d1b20]',
    activeButton: 'border-[5px] border-white bg-flipbook-primary text-white',
    toolButton: 'grid size-[65px] place-items-center rounded-[8px] border-0 shadow-none',
    toolIcon: 'size-[30px]',
    strokeGrid:
      'flex min-h-[42px] w-[210px] items-center justify-center gap-3 rounded-full bg-[#f8b7c0] px-4 shadow-[0_4px_12px_0_rgba(92,31,38,0.2)]',
    strokeButton: 'grid size-7 place-items-center rounded-full border-0 bg-transparent',
    activeStrokeButton: 'bg-white/45',
    selectedColor: 'border-white',
    neutralColor: 'border-[#edbfc4]',
    colorGrid: 'grid w-[144px] grid-cols-3 gap-[9px]',
    colorButton: 'size-[41px] rounded-[4px] border-0',
    selectedColorPreview: 'mt-[9px] h-[66px] w-[144px] rounded-[8px]',
    strokeDot: 'bg-[#c43b54]',
    activeStrokeDot: 'bg-[#9f263b]',
  },
} as const

export default function DrawingToolPanel({
  selectedToolKey,
  selectedColor,
  strokeWidth,
  colors,
  tone = 'relay',
  sections = DEFAULT_PANEL_SECTIONS,
  showSectionLabels = true,
  showSelectedColorPreview = false,
  strokeWidthOptions = STROKE_WIDTH_OPTIONS,
  className,
  onSelectTool,
  onSelectColor,
  onStrokeWidthChange,
  onUndoDrawing,
  onRedoDrawing,
  onClearDrawing,
}: DrawingToolPanelProps) {
  const toneStyle = TONE_STYLES[tone]
  const shouldShowTools = sections.includes('tools')
  const shouldShowStroke = sections.includes('stroke')
  const shouldShowColors = sections.includes('colors')

  return (
    <div className={cn(toneStyle.panel, className)}>
      {shouldShowTools && (
        <section className="grid gap-3">
          {showSectionLabels && <p className={cn('h4-b', toneStyle.heading)}>도구</p>}
          <div className={toneStyle.toolGrid}>
            {DRAWING_TOOL_BUTTONS.map((tool) => {
              const isActive = tool.action === 'select' && selectedToolKey === tool.key
              const Icon = tool.Icon

              return (
                <button
                  key={tool.key}
                  type="button"
                  title={tool.label}
                  aria-label={tool.label}
                  onClick={() => {
                    if (tool.action === 'undo') {
                      onUndoDrawing()
                      return
                    }
                    if (tool.action === 'redo') {
                      onRedoDrawing?.()
                      return
                    }
                    if (tool.action === 'clear') {
                      onClearDrawing()
                      return
                    }
                    onSelectTool(tool.key as DrawingToolKey)
                  }}
                  className={cn(
                    toneStyle.toolButton,
                    toneStyle.button,
                    isActive && toneStyle.activeButton,
                  )}
                >
                  <Icon className={toneStyle.toolIcon} aria-hidden />
                </button>
              )
            })}
          </div>
        </section>
      )}

      {shouldShowStroke && (
        <section className="grid gap-3">
          {showSectionLabels && <p className={cn('h4-b', toneStyle.heading)}>굵기</p>}
          <div className={toneStyle.strokeGrid}>
            {strokeWidthOptions.map((strokeWidthOption) => (
              <button
                key={strokeWidthOption}
                type="button"
                aria-label={`${strokeWidthOption}px 굵기`}
                onClick={() => onStrokeWidthChange(strokeWidthOption)}
                className={cn(
                  toneStyle.strokeButton,
                  toneStyle.button,
                  strokeWidth === strokeWidthOption && toneStyle.activeStrokeButton,
                )}
              >
                <span
                  className={cn(
                    'rounded-full',
                    toneStyle.strokeDot,
                    strokeWidth === strokeWidthOption && toneStyle.activeStrokeDot,
                  )}
                  style={{
                    width: `${strokeWidthOption}px`,
                    height: `${strokeWidthOption}px`,
                  }}
                />
              </button>
            ))}
          </div>
        </section>
      )}

      {shouldShowColors && (
        <section className="grid gap-3">
          {showSectionLabels && <p className={cn('h4-b', toneStyle.heading)}>색</p>}
          <div className={toneStyle.colorGrid}>
            {colors.map((color) => (
              <button
                key={color}
                type="button"
                aria-label={`${color} 색상`}
                onClick={() => onSelectColor(color)}
                className={cn(
                  toneStyle.colorButton,
                  selectedColor === color &&
                    cn(
                      tone === 'flipbook' ? 'border-[5px]' : 'border-[3px]',
                      toneStyle.selectedColor,
                    ),
                  color === '#ffffff' && toneStyle.neutralColor,
                )}
                style={{ backgroundColor: color }}
              />
            ))}
          </div>
          {showSelectedColorPreview && (
            <div
              aria-hidden
              className={toneStyle.selectedColorPreview}
              style={{ backgroundColor: selectedColor }}
            />
          )}
        </section>
      )}
    </div>
  )
}
