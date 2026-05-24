import Image from 'next/image'

import type { InfiniteCanvasColorOption } from '..'

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

        return (
          <button
            key={option.id}
            type="button"
            className="infinite-canvas-color-swatch"
            role="radio"
            aria-checked={isSelected}
            aria-label={option.label}
            data-selected={isSelected}
            onClick={() => onSelectColor(option.value)}
          >
            <Image
              src={option.assetSrc}
              alt=""
              aria-hidden
              width={288}
              height={288}
              draggable={false}
              className="infinite-canvas-color-swatch__image"
            />
          </button>
        )
      })}
    </div>
  )
}
