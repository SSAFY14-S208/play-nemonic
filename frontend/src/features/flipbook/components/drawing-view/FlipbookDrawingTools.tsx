'use client'

import type { DrawingToolKey } from '@/shared/types'
import { cn } from '@/shared/libs'

type FigmaDrawingToolKey = Extract<DrawingToolKey, 'pencil' | 'eraser' | 'bucket'>
type ToolItemKey = FigmaDrawingToolKey | 'clear' | 'undo' | 'redo'

const FLIPBOOK_TOOL_ICONS: Record<ToolItemKey, string> = {
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
  { key: 'clear', label: '전체 지우기' },
  { key: 'undo', label: '실행 취소' },
  { key: 'redo', label: '다시 실행' },
]

interface DrawingToolControlProps {
  selectedToolKey: DrawingToolKey
  canUndoDrawing: boolean
  canRedoDrawing: boolean
  onSelectTool: (toolKey: DrawingToolKey) => void
  onUndoDrawing: () => void
  onRedoDrawing: () => void
  onClearDrawing: () => void
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
