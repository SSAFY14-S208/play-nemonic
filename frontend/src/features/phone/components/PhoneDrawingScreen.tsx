'use client'

import Image from 'next/image'
import { useState } from 'react'
import {
  phoneDrawingRedo,
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
} from '../constants'
import { usePhoneDrawing } from '../hooks'
import { usePhoneStore } from '../phoneStore'
import {
  DrawingActionButtonRow,
  DrawingBackIcon,
  DrawingSliderControl,
  DrawingToolButton,
  PhoneDrawingStage,
} from './drawing-screen'

export function PhoneDrawingScreen() {
  const addDrawingArtifact = usePhoneStore((state) => state.addDrawingArtifact)
  const goHome = usePhoneStore((state) => state.goHome)

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
  } = usePhoneDrawing(addDrawingArtifact)

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
      onClick: () => {
        setActiveTool('pen')
        setIsToolControlOpen(true)
      },
      style: PHONE_DRAWING_LAYOUT.toolPen,
      toolKey: 'pen' as const,
    },
    {
      key: 'eraser',
      icon: isEraserActive
        ? phoneDrawingToolEraserActive
        : phoneDrawingToolEraser,
      label: '지우개',
      onClick: () => {
        setActiveTool('eraser')
        setIsToolControlOpen(true)
      },
      style: PHONE_DRAWING_LAYOUT.toolEraser,
      toolKey: 'eraser' as const,
    },
    {
      key: 'trash',
      icon: phoneDrawingToolTrash,
      label: '전체 지우기',
      onClick: clearDrawing,
      style: PHONE_DRAWING_LAYOUT.toolTrash,
      toolKey: undefined,
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
        onClick={goHome}
        className="absolute z-20 transition hover:scale-105 focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2"
        style={PHONE_DRAWING_LAYOUT.backButton}
      >
        <DrawingBackIcon />
      </button>

      <h2
        className="phone-drawing-title absolute z-20 text-center"
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
          onClick={toolButton.onClick}
          style={toolButton.style}
          toolKey={toolButton.toolKey}
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

      <DrawingActionButtonRow onCreateArtifact={createArtifact} />
    </div>
  )
}
