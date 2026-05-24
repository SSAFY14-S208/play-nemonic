import type { CSSProperties } from 'react'
import Image, { type StaticImageData } from 'next/image'
import { cn } from '@/shared/libs'
import type { PhoneDrawingToolKey } from '../..'

interface DrawingToolButtonProps {
  activeTool: PhoneDrawingToolKey
  icon: StaticImageData
  label: string
  onClick: () => void
  style: CSSProperties
  toolKey?: PhoneDrawingToolKey
}

export function DrawingToolButton({
  activeTool,
  icon,
  label,
  onClick,
  style,
  toolKey,
}: DrawingToolButtonProps) {
  const isActive = toolKey === activeTool

  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className={cn(
        'absolute grid place-items-center transition hover:scale-105 focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2',
        isActive && 'scale-105',
      )}
      style={style}
    >
      <Image src={icon} alt="" aria-hidden className="size-full object-contain" />
    </button>
  )
}
