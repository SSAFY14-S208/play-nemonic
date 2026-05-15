import type { CSSProperties } from 'react'

import type { InfiniteCanvasColorOption } from '../constants'

interface InfiniteCanvasColorPickerProps {
  options: readonly InfiniteCanvasColorOption[]
  selectedColor: string
  onSelectColor: (color: string) => void
}

export default function InfiniteCanvasColorPicker({
  options,
  selectedColor,
  onSelectColor,
}: InfiniteCanvasColorPickerProps) {
  return (
    <div className="infinite-canvas-color-picker" role="radiogroup" aria-label="색 고르기">
      {options.map((option) => {
        const isSelected = option.value === selectedColor
        const swatchStyle = {
          '--infinite-canvas-swatch-color': option.value,
        } as CSSProperties

        return (
          <button
            key={option.id}
            type="button"
            className="infinite-canvas-color-swatch"
            style={swatchStyle}
            role="radio"
            aria-checked={isSelected}
            aria-label={option.label}
            data-selected={isSelected}
            onClick={() => onSelectColor(option.value)}
          />
        )
      })}
    </div>
  )
}
