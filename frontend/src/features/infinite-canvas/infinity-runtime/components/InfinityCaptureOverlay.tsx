'use client'

import { useMemo, useRef, useState } from 'react'
import { RefreshCw, X } from 'lucide-react'
import { Button } from '@/shared/components'
import { cn } from '@/shared/libs'

export type InfinityCaptureRatio = 'free' | '1:1' | '4:3' | '3:4' | '16:9' | '9:16'

export interface InfinityCaptureRect {
  x: number
  y: number
  width: number
  height: number
}

interface InfinityCaptureOverlayProps {
  isSaving: boolean
  onCancel: () => void
  onCapture: (rect: InfinityCaptureRect, ratio: InfinityCaptureRatio) => void
}

const CAPTURE_RATIOS: {
  key: InfinityCaptureRatio
  label: string
  value: number | null
}[] = [
  { key: 'free', label: '자유', value: null },
  { key: '1:1', label: '1:1', value: 1 },
  { key: '4:3', label: '4:3', value: 4 / 3 },
  { key: '3:4', label: '3:4', value: 3 / 4 },
  { key: '16:9', label: '16:9', value: 16 / 9 },
  { key: '9:16', label: '9:16', value: 9 / 16 },
]

const MIN_CAPTURE_SIZE = 24

type CapturePoint = { x: number; y: number }
type CaptureHandle = 'nw' | 'ne' | 'sw' | 'se'

type CaptureDragState =
  | { kind: 'base'; start: CapturePoint }
  | { kind: 'move-crop'; offset: CapturePoint }
  | { kind: 'resize-crop'; anchor: CapturePoint }

function clamp(value: number, minValue: number, maxValue: number) {
  return Math.min(Math.max(value, minValue), maxValue)
}

function clampPoint(point: CapturePoint, bounds: InfinityCaptureRect) {
  return {
    x: clamp(point.x, bounds.x, bounds.x + bounds.width),
    y: clamp(point.y, bounds.y, bounds.y + bounds.height),
  }
}

function containsPoint(rect: InfinityCaptureRect, point: CapturePoint) {
  return (
    point.x >= rect.x &&
    point.x <= rect.x + rect.width &&
    point.y >= rect.y &&
    point.y <= rect.y + rect.height
  )
}

function isUsableRect(rect: InfinityCaptureRect | null) {
  return rect !== null && rect.width >= MIN_CAPTURE_SIZE && rect.height >= MIN_CAPTURE_SIZE
}

function normalizeFreeRect(start: CapturePoint, end: CapturePoint) {
  const width = end.x - start.x
  const height = end.y - start.y

  return {
    x: Math.min(start.x, start.x + width),
    y: Math.min(start.y, start.y + height),
    width: Math.abs(width),
    height: Math.abs(height),
  }
}

function fitRatioInsideRect(rect: InfinityCaptureRect, ratio: number | null) {
  if (ratio === null) return rect

  const baseRatio = rect.width / Math.max(rect.height, 1)
  let width = rect.width
  let height = rect.height

  if (baseRatio > ratio) {
    height = rect.height
    width = height * ratio
  } else {
    width = rect.width
    height = width / ratio
  }

  return {
    x: rect.x + (rect.width - width) / 2,
    y: rect.y + (rect.height - height) / 2,
    width,
    height,
  }
}

function fitRatioAroundRect(
  rect: InfinityCaptureRect,
  ratio: number | null,
  bounds: InfinityCaptureRect,
) {
  if (ratio === null) return moveRectInsideBounds(rect, bounds)

  const center = {
    x: rect.x + rect.width / 2,
    y: rect.y + rect.height / 2,
  }
  const currentRatio = rect.width / Math.max(rect.height, 1)
  let width = rect.width
  let height = rect.height

  if (currentRatio > ratio) {
    width = height * ratio
  } else {
    height = width / ratio
  }

  const maxRect = fitRatioInsideRect(bounds, ratio)
  if (width > maxRect.width) {
    width = maxRect.width
    height = width / ratio
  }
  if (height > maxRect.height) {
    height = maxRect.height
    width = height * ratio
  }

  return moveRectInsideBounds(
    {
      x: center.x - width / 2,
      y: center.y - height / 2,
      width,
      height,
    },
    bounds,
  )
}

function moveRectInsideBounds(rect: InfinityCaptureRect, bounds: InfinityCaptureRect) {
  return {
    ...rect,
    x: clamp(rect.x, bounds.x, bounds.x + bounds.width - rect.width),
    y: clamp(rect.y, bounds.y, bounds.y + bounds.height - rect.height),
  }
}

function normalizeRatioRectInsideBounds(
  anchor: CapturePoint,
  rawEnd: CapturePoint,
  ratio: number | null,
  bounds: InfinityCaptureRect,
) {
  const end = clampPoint(rawEnd, bounds)
  if (ratio === null) return normalizeFreeRect(anchor, end)

  const directionX = end.x < anchor.x ? -1 : 1
  const directionY = end.y < anchor.y ? -1 : 1
  const maxWidth = directionX > 0 ? bounds.x + bounds.width - anchor.x : anchor.x - bounds.x
  const maxHeight = directionY > 0 ? bounds.y + bounds.height - anchor.y : anchor.y - bounds.y

  let width = Math.abs(end.x - anchor.x)
  let height = Math.abs(end.y - anchor.y)

  if (width / Math.max(height, 1) > ratio) {
    height = width / ratio
  } else {
    width = height * ratio
  }

  if (width > maxWidth) {
    width = maxWidth
    height = width / ratio
  }
  if (height > maxHeight) {
    height = maxHeight
    width = height * ratio
  }

  return normalizeFreeRect(anchor, {
    x: anchor.x + width * directionX,
    y: anchor.y + height * directionY,
  })
}

function resizeRectFromCenter(
  rect: InfinityCaptureRect,
  bounds: InfinityCaptureRect,
  scale: number,
  ratio: number | null,
) {
  const effectiveRatio = ratio ?? rect.width / Math.max(rect.height, 1)
  const maxRect = fitRatioInsideRect(bounds, effectiveRatio)
  const nextWidth = clamp(rect.width * scale, MIN_CAPTURE_SIZE, maxRect.width)
  const nextHeight = ratio === null ? clamp(rect.height * scale, MIN_CAPTURE_SIZE, maxRect.height) : nextWidth / ratio
  const center = {
    x: rect.x + rect.width / 2,
    y: rect.y + rect.height / 2,
  }

  return moveRectInsideBounds(
    {
      x: center.x - nextWidth / 2,
      y: center.y - nextHeight / 2,
      width: nextWidth,
      height: nextHeight,
    },
    bounds,
  )
}

function getOppositeCorner(rect: InfinityCaptureRect, handle: CaptureHandle) {
  if (handle === 'nw') return { x: rect.x + rect.width, y: rect.y + rect.height }
  if (handle === 'ne') return { x: rect.x, y: rect.y + rect.height }
  if (handle === 'sw') return { x: rect.x + rect.width, y: rect.y }
  return { x: rect.x, y: rect.y }
}

function getElementBoundsRect(target: HTMLDivElement): InfinityCaptureRect {
  const bounds = target.getBoundingClientRect()
  return {
    x: 0,
    y: 0,
    width: bounds.width,
    height: bounds.height,
  }
}

export function InfinityCaptureOverlay({
  isSaving,
  onCancel,
  onCapture,
}: InfinityCaptureOverlayProps) {
  const [ratio, setRatio] = useState<InfinityCaptureRatio>('free')
  const [baseRect, setBaseRect] = useState<InfinityCaptureRect | null>(null)
  const [captureRect, setCaptureRect] = useState<InfinityCaptureRect | null>(null)
  const interactionLayerRef = useRef<HTMLDivElement>(null)
  const dragStateRef = useRef<CaptureDragState | null>(null)

  const ratioValue = useMemo(
    () => CAPTURE_RATIOS.find((captureRatio) => captureRatio.key === ratio)?.value ?? null,
    [ratio],
  )
  const hasCaptureRect = isUsableRect(captureRect)
  const canCapture = hasCaptureRect

  const selectRatio = (nextRatio: InfinityCaptureRatio) => {
    const nextRatioValue =
      CAPTURE_RATIOS.find((captureRatio) => captureRatio.key === nextRatio)?.value ?? null
    setRatio(nextRatio)
    if (!captureRect || !interactionLayerRef.current) return
    setCaptureRect(
      fitRatioAroundRect(
        captureRect,
        nextRatioValue,
        getElementBoundsRect(interactionLayerRef.current),
      ),
    )
  }

  const resetSelection = () => {
    dragStateRef.current = null
    setBaseRect(null)
    setCaptureRect(null)
    setRatio('free')
  }

  const getRelativePoint = (clientX: number, clientY: number, target: HTMLDivElement) => {
    const bounds = target.getBoundingClientRect()
    return {
      x: clientX - bounds.left,
      y: clientY - bounds.top,
    }
  }

  const updateDrag = (clientX: number, clientY: number, target: HTMLDivElement) => {
    const dragState = dragStateRef.current
    if (!dragState) return
    const pointer = getRelativePoint(clientX, clientY, target)

    if (dragState.kind === 'base') {
      const nextBaseRect = normalizeFreeRect(dragState.start, pointer)
      setBaseRect(nextBaseRect)
      setCaptureRect(nextBaseRect)
      return
    }

    if (!captureRect) return
    const dragBounds = getElementBoundsRect(target)

    if (dragState.kind === 'move-crop') {
      setCaptureRect(
        moveRectInsideBounds(
          {
            ...captureRect,
            x: pointer.x - dragState.offset.x,
            y: pointer.y - dragState.offset.y,
          },
          dragBounds,
        ),
      )
      return
    }

    setCaptureRect(
      normalizeRatioRectInsideBounds(dragState.anchor, pointer, ratioValue, dragBounds),
    )
  }

  const finishDrag = (clientX: number, clientY: number, target: HTMLDivElement) => {
    const dragState = dragStateRef.current
    if (!dragState) return
    updateDrag(clientX, clientY, target)
    dragStateRef.current = null

    if (dragState.kind !== 'base') return

    const pointer = getRelativePoint(clientX, clientY, target)
    const nextBaseRect = normalizeFreeRect(dragState.start, pointer)
    if (isUsableRect(nextBaseRect)) {
      setBaseRect(nextBaseRect)
      setCaptureRect(fitRatioInsideRect(nextBaseRect, ratioValue))
    } else {
      resetSelection()
    }
  }

  const handlePointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return
    event.currentTarget.setPointerCapture(event.pointerId)
    const pointer = getRelativePoint(event.clientX, event.clientY, event.currentTarget)
    const handle = (event.target as HTMLElement).dataset.captureHandle as CaptureHandle | undefined

    if (baseRect && captureRect && handle) {
      dragStateRef.current = {
        kind: 'resize-crop',
        anchor: getOppositeCorner(captureRect, handle),
      }
      return
    }

    if (captureRect && containsPoint(captureRect, pointer)) {
      dragStateRef.current = {
        kind: 'move-crop',
        offset: {
          x: pointer.x - captureRect.x,
          y: pointer.y - captureRect.y,
        },
      }
      return
    }

    dragStateRef.current = {
      kind: 'base',
      start: pointer,
    }
    setBaseRect({ x: pointer.x, y: pointer.y, width: 0, height: 0 })
    setCaptureRect({ x: pointer.x, y: pointer.y, width: 0, height: 0 })
    setRatio('free')
  }

  const handlePointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    updateDrag(event.clientX, event.clientY, event.currentTarget)
  }

  const handlePointerUp = (event: React.PointerEvent<HTMLDivElement>) => {
    finishDrag(event.clientX, event.clientY, event.currentTarget)
  }

  const handleWheel = (event: React.WheelEvent<HTMLDivElement>) => {
    if (!captureRect) return
    event.preventDefault()
    const scale = event.deltaY < 0 ? 1.08 : 0.92
    setCaptureRect(resizeRectFromCenter(captureRect, getElementBoundsRect(event.currentTarget), scale, ratioValue))
  }

  return (
    <div className="absolute inset-0 z-30">
      <div
        ref={interactionLayerRef}
        className="absolute inset-0 cursor-crosshair bg-slate-950/34"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onWheel={handleWheel}
      >
        {captureRect && (
          <div
            className="absolute cursor-move border-2 border-white bg-white/10 shadow-[0_0_0_9999px_rgb(15_23_42_/_38%)]"
            style={{
              left: captureRect.x,
              top: captureRect.y,
              width: captureRect.width,
              height: captureRect.height,
            }}
          >
            {(['nw', 'ne', 'sw', 'se'] as const).map((handle) => (
              <span
                key={handle}
                data-capture-handle={handle}
                className={cn(
                  'absolute size-3 rounded-full border-2 border-white bg-canvas-accent shadow-md',
                  handle.includes('n') ? 'top-[-7px]' : 'bottom-[-7px]',
                  handle.includes('w') ? 'left-[-7px]' : 'right-[-7px]',
                )}
              />
            ))}
          </div>
        )}
      </div>

      {hasCaptureRect && (
        <div className="pointer-events-auto absolute left-1/2 top-6 flex -translate-x-1/2 items-center gap-4 rounded-full border border-white/70 bg-[linear-gradient(135deg,#7c61ff_0%,#2e73f2_48%,#5dc7f2_100%)] px-5 py-4 text-white shadow-[0_16px_34px_rgba(64,95,220,0.34),inset_0_1px_0_rgba(255,255,255,0.5)]">
          {CAPTURE_RATIOS.map((captureRatio) => (
            <button
              key={captureRatio.key}
              type="button"
              onClick={() => selectRatio(captureRatio.key)}
              className={cn(
                'h2-b min-h-16 rounded-full px-6 text-white/88 transition-colors hover:bg-white/18 hover:text-white',
                ratio === captureRatio.key &&
                  'bg-white text-[#285ed8] shadow-[0_8px_18px_rgba(22,58,160,0.2)]',
              )}
            >
              {captureRatio.label}
            </button>
          ))}
          <Button
            type="button"
            size="sm"
            disabled={!canCapture || isSaving}
            onClick={() => {
              if (!captureRect) return
              onCapture(captureRect, ratio)
            }}
            className="h2-b min-h-20 px-10 bg-[#6d5df6] text-white shadow-[0_10px_18px_rgba(72,75,210,0.24)] hover:bg-[#5847e8]"
          >
            {isSaving ? '캡쳐 중' : '스크린캡쳐'}
          </Button>
          <button
            type="button"
            aria-label="스크린캡쳐 영역 다시 선택"
            title="스크린캡쳐 영역 다시 선택"
            onClick={resetSelection}
            className="grid size-16 place-items-center rounded-full bg-[#6d5df6] text-white shadow-[0_10px_18px_rgba(72,75,210,0.24)] transition-colors hover:bg-[#5847e8]"
          >
            <RefreshCw className="size-7" aria-hidden />
          </button>
        </div>
      )}

      <button
        type="button"
        aria-label="스크린캡쳐 선택 취소"
        onClick={onCancel}
        className="pointer-events-auto absolute bottom-14 left-1/2 grid size-16 -translate-x-1/2 place-items-center rounded-full border border-white/72 bg-[#3aa7f4] text-white shadow-[0_12px_24px_rgba(46,95,210,0.26),inset_0_1px_0_rgba(255,255,255,0.44)] transition-transform hover:scale-105 focus-visible:outline-none"
      >
        <X className="size-7" aria-hidden />
      </button>
    </div>
  )
}
