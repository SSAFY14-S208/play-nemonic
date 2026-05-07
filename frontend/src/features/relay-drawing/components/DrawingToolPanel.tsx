'use client'

import { RELAY_COLORS, type RelayToolKey } from '../constants'
import { DrawingToolPanel as SharedDrawingToolPanel } from '@/shared/components'
import type { DrawingToolKey } from '@/shared/types'

interface DrawingToolPanelProps {
  selectedToolKey: RelayToolKey
  selectedColor: string
  strokeWidth: number
  onSelectTool: (tool: RelayToolKey) => void
  onSelectColor: (color: string) => void
  onStrokeWidthChange: (strokeWidth: number) => void
  onUndoDrawing: () => void
  onClearDrawing: () => void
}

export default function DrawingToolPanel({
  selectedToolKey,
  selectedColor,
  strokeWidth,
  onSelectTool,
  onSelectColor,
  onStrokeWidthChange,
  onUndoDrawing,
  onClearDrawing,
}: DrawingToolPanelProps) {
  return (
    <SharedDrawingToolPanel
      tone="relay"
      selectedToolKey={selectedToolKey as DrawingToolKey}
      selectedColor={selectedColor}
      strokeWidth={strokeWidth}
      colors={RELAY_COLORS}
      onSelectTool={(toolKey) => onSelectTool(toolKey as RelayToolKey)}
      onSelectColor={onSelectColor}
      onStrokeWidthChange={onStrokeWidthChange}
      onUndoDrawing={onUndoDrawing}
      onClearDrawing={onClearDrawing}
    />
  )
}
