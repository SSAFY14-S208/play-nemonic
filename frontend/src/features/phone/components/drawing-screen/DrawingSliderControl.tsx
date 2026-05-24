import Image from 'next/image'
import { phoneDrawingClose } from '@/shared/assets'
import { PHONE_COLORS, PHONE_DRAWING_LAYOUT, PHONE_MAX_BRUSH_SIZE, PHONE_MIN_BRUSH_SIZE } from '../../constants'
import type { PhoneDrawingToolKey } from '../..'
import { getBrushSizePercent } from './drawingScreenUtils'

interface DrawingSliderControlProps {
  activeTool: PhoneDrawingToolKey
  brushSize: number
  onClose: () => void
  selectedColor: string
  setBrushSize: (brushSize: number) => void
}

function DrawingThicknessSlider({
  activeTool,
  brushSize,
  setBrushSize,
}: {
  activeTool: PhoneDrawingToolKey
  brushSize: number
  setBrushSize: (brushSize: number) => void
}) {
  const brushPercent = getBrushSizePercent(brushSize)

  return (
    <div
      className="absolute"
      style={PHONE_DRAWING_LAYOUT.sliderTrackByTool[activeTool]}
    >
      <div
        aria-hidden
        className="absolute left-0 top-1/2 h-[7.8%] w-full -translate-y-1/2 rounded-full"
        style={{ background: PHONE_COLORS.drawingSliderTrack }}
      />
      <div
        aria-hidden
        className="absolute left-0 top-1/2 h-[7.8%] -translate-y-1/2 rounded-full"
        style={{
          background: PHONE_COLORS.drawingAccent,
          width: `${brushPercent}%`,
        }}
      />
      <span
        aria-hidden
        className="absolute top-1/2 aspect-square h-full -translate-x-1/2 -translate-y-1/2 rounded-full border-[2px]"
        style={{
          background: PHONE_COLORS.drawingSliderThumb,
          borderColor: PHONE_COLORS.drawingIcon,
          boxShadow: PHONE_COLORS.drawingSliderThumbShadow,
          left: `${brushPercent}%`,
        }}
      />
      <input
        aria-label={activeTool === 'pen' ? '펜 굵기' : '지우개 굵기'}
        type="range"
        min={PHONE_MIN_BRUSH_SIZE}
        max={PHONE_MAX_BRUSH_SIZE}
        step={1}
        value={brushSize}
        onChange={(event) => setBrushSize(Number(event.target.value))}
        className="absolute -inset-y-4 inset-x-0 cursor-pointer opacity-0"
      />
    </div>
  )
}

export function DrawingSliderControl({
  activeTool,
  brushSize,
  onClose,
  selectedColor,
  setBrushSize,
}: DrawingSliderControlProps) {
  const isPenActive = activeTool === 'pen'

  return (
    <div className="absolute z-10" style={PHONE_DRAWING_LAYOUT.sliderPanel}>
      {isPenActive && (
        <div
          className="absolute grid place-items-center"
          style={PHONE_DRAWING_LAYOUT.sliderPreview}
        >
          <svg
            aria-hidden
            className="h-[72%] w-[84%]"
            viewBox="0 0 136 34"
            fill="none"
          >
            <path
              d="M8 18C34 12 53 22 78 18C97 15 111 12 128 14"
              stroke={selectedColor}
              strokeLinecap="round"
              strokeWidth={Math.max(brushSize, 5)}
            />
          </svg>
        </div>
      )}
      <DrawingThicknessSlider
        activeTool={activeTool}
        brushSize={brushSize}
        setBrushSize={setBrushSize}
      />
      <button
        type="button"
        aria-label="조절 도구 닫기"
        onClick={onClose}
        className="absolute transition hover:scale-105"
        style={PHONE_DRAWING_LAYOUT.sliderClose}
      >
        <Image
          src={phoneDrawingClose}
          alt=""
          aria-hidden
          fill
          className="object-contain"
          sizes="24px"
        />
      </button>
    </div>
  )
}
