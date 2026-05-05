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
  onSelectTool: (toolKey: DrawingToolKey) => void
  onSelectColor: (color: string) => void
  onStrokeWidthChange: (strokeWidth: number) => void
  onUndoDrawing: () => void
  onRedoDrawing?: () => void
  onClearDrawing: () => void
}

const DRAWING_TOOL_BUTTONS: DrawingToolButton[] = [
  { key: 'pencil', label: '펜', Icon: Pencil, action: 'select' },
  { key: 'bucket', label: '채우기', Icon: PaintBucket, action: 'select' },
  { key: 'eraser', label: '지우개', Icon: Eraser, action: 'select' },
  { key: 'undo', label: '되돌리기', Icon: Undo2, action: 'undo' },
  { key: 'redo', label: '다시 실행', Icon: Redo2, action: 'redo' },
  { key: 'clear', label: '전체 지우기', Icon: Trash2, action: 'clear' },
]

const STROKE_WIDTH_OPTIONS = [3, 6, 10]

const TONE_STYLES = {
  relay: {
    heading: 'text-relay-ink',
    button: 'border-relay-line bg-relay-active text-relay-ink',
    activeButton: 'border-relay-accent-strong bg-relay-accent',
    selectedColor: 'border-relay-accent-strong',
    neutralColor: 'border-relay-dash',
    strokeDot: 'bg-relay-dash',
  },
  flipbook: {
    heading: 'text-flipbook-ink',
    button: 'border-flipbook-primary bg-flipbook-light text-flipbook-ink',
    activeButton: 'border-flipbook-deep bg-flipbook-primary',
    selectedColor: 'border-flipbook-deep',
    neutralColor: 'border-flipbook-muted',
    strokeDot: 'bg-flipbook-muted',
  },
} as const

export default function DrawingToolPanel({
  selectedToolKey,
  selectedColor,
  strokeWidth,
  colors,
  tone = 'relay',
  onSelectTool,
  onSelectColor,
  onStrokeWidthChange,
  onUndoDrawing,
  onRedoDrawing,
  onClearDrawing,
}: DrawingToolPanelProps) {
  const toneStyle = TONE_STYLES[tone]

  return (
    <div className="grid gap-6">
      <section className="grid gap-3">
        <p className={cn('h4-b', toneStyle.heading)}>도구</p>
        <div className="grid w-fit grid-cols-3 gap-3">
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
                  'grid size-11 place-items-center rounded-[12px] border',
                  toneStyle.button,
                  isActive && toneStyle.activeButton,
                )}
              >
                <Icon className="size-[18px]" aria-hidden />
              </button>
            )
          })}
        </div>
      </section>

      <section className="grid gap-3">
        <p className={cn('h4-b', toneStyle.heading)}>굵기</p>
        <div className="grid w-full grid-cols-3 gap-3">
          {STROKE_WIDTH_OPTIONS.map((strokeWidthOption) => (
            <button
              key={strokeWidthOption}
              type="button"
              aria-label={`${strokeWidthOption}px 굵기`}
              onClick={() => onStrokeWidthChange(strokeWidthOption)}
              className={cn(
                'grid min-h-10 place-items-center rounded-full border',
                toneStyle.button,
                strokeWidth === strokeWidthOption && toneStyle.activeButton,
              )}
            >
              <span
                className={cn('rounded-full', toneStyle.strokeDot)}
                style={{
                  width: `${strokeWidthOption}px`,
                  height: `${strokeWidthOption}px`,
                }}
              />
            </button>
          ))}
        </div>
      </section>

      <section className="grid gap-3">
        <p className={cn('h4-b', toneStyle.heading)}>색</p>
        <div className="grid w-fit grid-cols-4 gap-3">
          {colors.map((color) => (
            <button
              key={color}
              type="button"
              aria-label={`${color} 색상`}
              onClick={() => onSelectColor(color)}
              className={cn(
                'size-9 rounded-full border border-transparent',
                selectedColor === color && cn('border-[3px]', toneStyle.selectedColor),
                color === '#ffffff' && toneStyle.neutralColor,
              )}
              style={{ backgroundColor: color }}
            />
          ))}
        </div>
      </section>
    </div>
  )
}
