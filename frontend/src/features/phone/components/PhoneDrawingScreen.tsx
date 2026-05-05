'use client'

import Image, { type StaticImageData } from 'next/image'
import { useState, type CSSProperties } from 'react'
import {
  phoneDrawingBackLineBottom,
  phoneDrawingBackLineTop,
  phoneDrawingClose,
  phoneDrawingPrint,
  phoneDrawingRedo,
  phoneDrawingSave,
  phoneDrawingToolEraser,
  phoneDrawingToolEraserActive,
  phoneDrawingToolPen,
  phoneDrawingToolPenInactive,
  phoneDrawingToolTrash,
  phoneDrawingUndo,
} from '@/shared/assets'
import { cn } from '@/shared/libs'
import {
  PHONE_COLORS,
  PHONE_DRAWING_LAYOUT,
  PHONE_MAX_BRUSH_SIZE,
  PHONE_MIN_BRUSH_SIZE,
} from '../constants'
import { usePhoneDrawing } from '../hooks'
import type { PhoneDrawingToolKey } from '../types'
import { PhoneDrawingStage } from './PhoneDrawingStage'

interface PhoneDrawingScreenProps {
  onBack: () => void
  onCreateArtifact: (imageDataUrl: string, action: 'save' | 'print') => void
}

interface DrawingToolButtonProps {
  icon: StaticImageData
  label: string
  style: CSSProperties
  toolKey?: PhoneDrawingToolKey
  activeTool: PhoneDrawingToolKey
  onClick: () => void
}

const DRAWING_ACTION_BUTTONS = [
  {
    action: 'save',
    icon: phoneDrawingSave,
    iconClassName: 'h-[42%] w-[16.3%]',
    label: '갤러리에 저장',
    style: PHONE_DRAWING_LAYOUT.saveButton,
  },
  {
    action: 'print',
    icon: phoneDrawingPrint,
    iconClassName: 'h-[42%] w-[15.9%]',
    label: '네모닉 출력',
    style: PHONE_DRAWING_LAYOUT.printButton,
  },
] as const

function DrawingBackIcon() {
  return (
    <span className="relative block h-full w-full">
      <Image
        src={phoneDrawingBackLineTop}
        alt=""
        aria-hidden
        className="absolute left-[10%] top-1/2 h-[22%] w-[86%] origin-left -translate-y-1/2 rotate-[-45deg]"
      />
      <Image
        src={phoneDrawingBackLineBottom}
        alt=""
        aria-hidden
        className="absolute left-[10%] top-1/2 h-[22%] w-[86%] origin-left -translate-y-1/2 rotate-45"
      />
    </span>
  )
}

function DrawingToolButton({
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

function getBrushSizePercent(brushSize: number) {
  return (
    ((brushSize - PHONE_MIN_BRUSH_SIZE) /
      (PHONE_MAX_BRUSH_SIZE - PHONE_MIN_BRUSH_SIZE)) *
    100
  )
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

function DrawingSliderControl({
  activeTool,
  brushSize,
  onClose,
  selectedColor,
  setBrushSize,
}: {
  activeTool: PhoneDrawingToolKey
  brushSize: number
  onClose: () => void
  selectedColor: string
  setBrushSize: (brushSize: number) => void
}) {
  const isPenActive = activeTool === 'pen'

  return (
    <div
      className="absolute z-10"
      style={PHONE_DRAWING_LAYOUT.sliderPanel}
    >
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

export function PhoneDrawingScreen({
  onBack,
  onCreateArtifact,
}: PhoneDrawingScreenProps) {
  const {
    activeTool,
    brushSize,
    clearDrawing,
    createArtifact,
    draw,
    endDrawing,
    lines,
    redoLines,
    redoDrawing,
    selectedColor,
    setActiveTool,
    setBrushSize,
    stageRef,
    startDrawing,
    undoDrawing,
  } = usePhoneDrawing(onCreateArtifact)

  const [isToolControlOpen, setIsToolControlOpen] = useState(true)
  const isPenActive = activeTool === 'pen'
  const isEraserActive = activeTool === 'eraser'
  const canUndoDrawing = lines.length > 0
  const canRedoDrawing = redoLines.length > 0

  const toolButtons = [
    {
      key: 'pen',
      icon: isPenActive ? phoneDrawingToolPen : phoneDrawingToolPenInactive,
      label: '펜',
      style: PHONE_DRAWING_LAYOUT.toolPen,
      toolKey: 'pen' as const,
      onClick: () => {
        setActiveTool('pen')
        setIsToolControlOpen(true)
      },
    },
    {
      key: 'eraser',
      icon: isEraserActive
        ? phoneDrawingToolEraserActive
        : phoneDrawingToolEraser,
      label: '지우개',
      style: PHONE_DRAWING_LAYOUT.toolEraser,
      toolKey: 'eraser' as const,
      onClick: () => {
        setActiveTool('eraser')
        setIsToolControlOpen(true)
      },
    },
    {
      key: 'trash',
      icon: phoneDrawingToolTrash,
      label: '전체 지우기',
      style: PHONE_DRAWING_LAYOUT.toolTrash,
      onClick: clearDrawing,
    },
  ]

  return (
    <div
      className="relative h-full overflow-hidden"
      style={{
        background: PHONE_COLORS.drawingBackground,
        color: PHONE_COLORS.drawingText,
      }}
    >
      <div className="absolute" style={PHONE_DRAWING_LAYOUT.backgroundTop} />
      <div
        className="absolute"
        style={PHONE_DRAWING_LAYOUT.backgroundMiddle}
      />
      <div
        className="absolute"
        style={PHONE_DRAWING_LAYOUT.backgroundBottom}
      />

      <button
        type="button"
        aria-label="홈으로 돌아가기"
        onClick={onBack}
        className="absolute z-20 transition hover:scale-105 focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2"
        style={PHONE_DRAWING_LAYOUT.backButton}
      >
        <DrawingBackIcon />
      </button>

      <h2
        className="h2-b absolute z-20 text-center text-[1.04rem] leading-tight"
        style={PHONE_DRAWING_LAYOUT.title}
      >
        네모닉 그림판
      </h2>

      <div
        className="absolute z-20 flex items-center justify-between"
        style={PHONE_DRAWING_LAYOUT.undoRedo}
      >
        <button
          type="button"
          aria-label="되돌리기"
          disabled={!canUndoDrawing}
          onClick={undoDrawing}
          className={cn(
            'relative h-full w-[31.02%] transition',
            canUndoDrawing && 'hover:scale-105',
          )}
        >
          <Image
            src={canUndoDrawing ? phoneDrawingUndo : phoneDrawingRedo}
            alt=""
            aria-hidden
            fill
            className="object-contain"
            sizes="28px"
          />
        </button>
        <button
          type="button"
          aria-label="다시 실행"
          disabled={!canRedoDrawing}
          onClick={redoDrawing}
          className={cn(
            'relative h-full w-[31.02%] transition',
            canRedoDrawing && 'hover:scale-105',
          )}
        >
          <Image
            src={canRedoDrawing ? phoneDrawingUndo : phoneDrawingRedo}
            alt=""
            aria-hidden
            fill
            className="-scale-x-100 object-contain"
            sizes="28px"
          />
        </button>
      </div>

      {toolButtons.map((toolButton) => (
        <DrawingToolButton
          key={toolButton.key}
          activeTool={activeTool}
          icon={toolButton.icon}
          label={toolButton.label}
          style={toolButton.style}
          toolKey={toolButton.toolKey}
          onClick={toolButton.onClick}
        />
      ))}

      {isToolControlOpen && (
        <DrawingSliderControl
          activeTool={activeTool}
          brushSize={brushSize}
          onClose={() => setIsToolControlOpen(false)}
          selectedColor={selectedColor}
          setBrushSize={setBrushSize}
        />
      )}

      <main
        className="absolute aspect-square"
        style={PHONE_DRAWING_LAYOUT.paper}
      >
        <div
          className="h-full w-full overflow-hidden"
          style={PHONE_DRAWING_LAYOUT.paperSurface}
        >
          <PhoneDrawingStage
            lines={lines}
            onDrawEnd={endDrawing}
            onDrawMove={draw}
            onDrawStart={startDrawing}
            stageRef={stageRef}
          />
        </div>
      </main>

      <footer
        className="absolute z-20"
        style={PHONE_DRAWING_LAYOUT.actionButtonRow}
      >
        {DRAWING_ACTION_BUTTONS.map((actionButton) => (
          <button
            key={actionButton.action}
            type="button"
            onClick={() => createArtifact(actionButton.action)}
            className="body-l-b flex h-full w-full items-center justify-center gap-[4.1%] text-[0.96rem] leading-tight transition hover:-translate-y-0.5"
            style={actionButton.style}
          >
            <span className={cn('relative block', actionButton.iconClassName)}>
              <Image
                src={actionButton.icon}
                alt=""
                aria-hidden
                fill
                className="object-contain"
                sizes="28px"
              />
            </span>
            <span className="text-center">{actionButton.label}</span>
          </button>
        ))}
      </footer>
    </div>
  )
}
