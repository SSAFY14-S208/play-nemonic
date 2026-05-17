'use client'

import { useRef } from 'react'
import type Konva from 'konva'
import type { InfiniteCanvasOperationRequest } from '@/shared/types'

import {
  type InfinityFill,
  type InfinityLine,
  type InfinityObject,
  type InfinityShape,
  type InfinityText,
  type InfinityToolKey,
  INFINITY_TEXT_DEFAULT_FONT_SIZE,
} from '../constants'

function generateId(): string {
  return Math.random().toString(36).slice(2, 9)
}

function flattenPoints(points: { x: number; y: number }[]): number[] {
  return points.flatMap((p) => [p.x, p.y])
}

const MIN_LINE_POINT_DISTANCE = 3
const MAX_LINE_POINTS_PER_OBJECT = 640
const MAX_DRAFT_LINE_POINTS = 180
const BUCKET_FILL_PADDING = 96
const BUCKET_FILL_MAX_SIZE = 1600
const BUCKET_FILL_ALPHA_TOLERANCE = 16
const BUCKET_FILL_COLOR_TOLERANCE = 12
const BUCKET_FILL_BARRIER_DILATION_PASSES = 2
const BUCKET_FILL_DILATION_PASSES = 6
const BUCKET_FILL_DILATION_COLOR_TOLERANCE = 96

function shouldAppendLinePoint(
  previousPoint: { x: number; y: number } | undefined,
  nextPoint: { x: number; y: number },
) {
  if (!previousPoint) return true
  const distanceX = nextPoint.x - previousPoint.x
  const distanceY = nextPoint.y - previousPoint.y
  return distanceX * distanceX + distanceY * distanceY >= MIN_LINE_POINT_DISTANCE * MIN_LINE_POINT_DISTANCE
}

function limitLinePoints(points: { x: number; y: number }[], maxPointCount: number) {
  if (points.length <= maxPointCount) return points
  if (maxPointCount <= 2) return [points[0], points[points.length - 1]]

  const sampledPoints: { x: number; y: number }[] = []
  const step = (points.length - 1) / (maxPointCount - 1)
  for (let pointIndex = 0; pointIndex < maxPointCount; pointIndex++) {
    sampledPoints.push(points[Math.round(pointIndex * step)])
  }
  return sampledPoints
}

function createDraftLine(line: InfinityLine): InfinityLine {
  return {
    ...line,
    points: limitLinePoints(line.points, MAX_DRAFT_LINE_POINTS),
  }
}

function createPersistedLine(line: InfinityLine): InfinityLine {
  return {
    ...line,
    points: limitLinePoints(line.points, MAX_LINE_POINTS_PER_OBJECT),
  }
}

function isShapeTool(tool: InfinityToolKey): boolean {
  return (
    tool === 'shape-rect' ||
    tool === 'shape-ellipse' ||
    tool === 'shape-rect-fill' ||
    tool === 'shape-ellipse-fill'
  )
}

function shapeTypeOfTool(tool: InfinityToolKey): 'rect' | 'ellipse' {
  return tool === 'shape-rect' || tool === 'shape-rect-fill'
    ? 'rect'
    : 'ellipse'
}

function isFillTool(tool: InfinityToolKey): boolean {
  return tool === 'shape-rect-fill' || tool === 'shape-ellipse-fill'
}

function recolorObject(object: InfinityObject, color: string): InfinityObject {
  if (object.type === 'fill') {
    return { ...object, color }
  }
  if (object.type === 'rect' || object.type === 'ellipse') {
    return { ...object, fill: color, color }
  }

  return { ...object, color }
}

function getObjectBounds(object: InfinityObject) {
  if (object.type === 'fill' || object.type === 'rect' || object.type === 'ellipse') {
    return {
      x: object.x,
      y: object.y,
      width: Math.abs(object.width),
      height: Math.abs(object.height),
    }
  }

  if (object.type === 'text') {
    return {
      x: object.x,
      y: object.y,
      width: Math.max(object.text.length * object.fontSize * 0.58, object.fontSize),
      height: object.fontSize * 1.35,
    }
  }

  if (object.type !== 'line' || object.points.length === 0) return null
  const xValues = object.points.map((point) => point.x)
  const yValues = object.points.map((point) => point.y)
  const minX = Math.min(...xValues)
  const maxX = Math.max(...xValues)
  const minY = Math.min(...yValues)
  const maxY = Math.max(...yValues)
  const padding = Math.max(object.strokeWidth, 8)

  return {
    x: minX - padding,
    y: minY - padding,
    width: Math.max(maxX - minX + padding * 2, padding * 2),
    height: Math.max(maxY - minY + padding * 2, padding * 2),
  }
}

function parseHexColor(hexColor: string) {
  const normalizedHex = hexColor.replace('#', '')
  return {
    red: Number.parseInt(normalizedHex.slice(0, 2), 16),
    green: Number.parseInt(normalizedHex.slice(2, 4), 16),
    blue: Number.parseInt(normalizedHex.slice(4, 6), 16),
    alpha: 255,
  }
}

function isPixelMatchingTarget(
  pixels: Uint8ClampedArray,
  pixelOffset: number,
  target: { red: number; green: number; blue: number; alpha: number },
) {
  const alpha = pixels[pixelOffset + 3]
  if (target.alpha <= BUCKET_FILL_ALPHA_TOLERANCE) {
    return alpha <= BUCKET_FILL_ALPHA_TOLERANCE
  }

  return (
    Math.abs(pixels[pixelOffset] - target.red) <= BUCKET_FILL_COLOR_TOLERANCE &&
    Math.abs(pixels[pixelOffset + 1] - target.green) <= BUCKET_FILL_COLOR_TOLERANCE &&
    Math.abs(pixels[pixelOffset + 2] - target.blue) <= BUCKET_FILL_COLOR_TOLERANCE &&
    Math.abs(alpha - target.alpha) <= BUCKET_FILL_COLOR_TOLERANCE
  )
}

function createDilatedBarrierPixels(pixels: Uint8ClampedArray, width: number, height: number) {
  let barrierPixels = new Uint8Array(width * height)

  for (let pixelIndex = 0; pixelIndex < width * height; pixelIndex++) {
    const alpha = pixels[pixelIndex * 4 + 3]
    if (alpha > BUCKET_FILL_ALPHA_TOLERANCE) {
      barrierPixels[pixelIndex] = 1
    }
  }

  for (let dilationPass = 0; dilationPass < BUCKET_FILL_BARRIER_DILATION_PASSES; dilationPass++) {
    const nextBarrierPixels = new Uint8Array(barrierPixels)

    for (let pixelIndex = 0; pixelIndex < width * height; pixelIndex++) {
      if (barrierPixels[pixelIndex] === 1) continue

      const x = pixelIndex % width
      const y = Math.floor(pixelIndex / width)
      const hasBarrierNeighbor =
        (x > 0 && barrierPixels[pixelIndex - 1] === 1) ||
        (x < width - 1 && barrierPixels[pixelIndex + 1] === 1) ||
        (y > 0 && barrierPixels[pixelIndex - width] === 1) ||
        (y < height - 1 && barrierPixels[pixelIndex + width] === 1)

      if (hasBarrierNeighbor) {
        nextBarrierPixels[pixelIndex] = 1
      }
    }

    barrierPixels = nextBarrierPixels
  }

  return barrierPixels
}

function drawObjectForBucketFill(
  context: CanvasRenderingContext2D,
  object: InfinityObject,
  origin: { x: number; y: number },
) {
  context.save()
  context.translate(-origin.x, -origin.y)

  if (object.type === 'line') {
    if (object.points.length > 0 && !object.isEraser) {
      const firstPoint = object.points[0]
      context.lineCap = 'round'
      context.lineJoin = 'round'
      context.lineWidth = object.strokeWidth
      context.strokeStyle = object.color
      context.beginPath()
      context.moveTo(firstPoint.x, firstPoint.y)
      object.points.slice(1).forEach((point) => context.lineTo(point.x, point.y))
      context.stroke()
    }
    context.restore()
    return
  }

  if (object.type === 'rect') {
    context.translate(object.x + object.width / 2, object.y + object.height / 2)
    context.rotate(((object.rotation ?? 0) * Math.PI) / 180)
    const x = -object.width / 2
    const y = -object.height / 2
    if (object.fill) {
      context.fillStyle = object.fill
      context.fillRect(x, y, object.width, object.height)
    } else {
      context.strokeStyle = object.color
      context.lineWidth = object.strokeWidth
      context.strokeRect(x, y, object.width, object.height)
    }
    context.restore()
    return
  }

  if (object.type === 'ellipse') {
    context.translate(object.x + object.width / 2, object.y + object.height / 2)
    context.rotate(((object.rotation ?? 0) * Math.PI) / 180)
    context.beginPath()
    context.ellipse(0, 0, Math.abs(object.width / 2), Math.abs(object.height / 2), 0, 0, Math.PI * 2)
    if (object.fill) {
      context.fillStyle = object.fill
      context.fill()
    } else {
      context.strokeStyle = object.color
      context.lineWidth = object.strokeWidth
      context.stroke()
    }
    context.restore()
    return
  }

  context.restore()
}

function createBucketFillObject({
  objects,
  pointerPosition,
  color,
}: {
  objects: InfinityObject[]
  pointerPosition: { x: number; y: number }
  color: string
}): InfinityFill | null {
  const bounds = objects
    .filter((object) => object.type !== 'fill' && object.type !== 'text')
    .map(getObjectBounds)
    .filter((bounds): bounds is { x: number; y: number; width: number; height: number } => Boolean(bounds))

  if (bounds.length === 0) return null

  const minX = Math.floor(Math.min(pointerPosition.x, ...bounds.map((bound) => bound.x)) - BUCKET_FILL_PADDING)
  const minY = Math.floor(Math.min(pointerPosition.y, ...bounds.map((bound) => bound.y)) - BUCKET_FILL_PADDING)
  const maxX = Math.ceil(Math.max(pointerPosition.x, ...bounds.map((bound) => bound.x + bound.width)) + BUCKET_FILL_PADDING)
  const maxY = Math.ceil(Math.max(pointerPosition.y, ...bounds.map((bound) => bound.y + bound.height)) + BUCKET_FILL_PADDING)
  const rawWidth = maxX - minX
  const rawHeight = maxY - minY
  if (rawWidth <= 0 || rawHeight <= 0 || rawWidth > BUCKET_FILL_MAX_SIZE || rawHeight > BUCKET_FILL_MAX_SIZE) {
    return null
  }

  const canvas = document.createElement('canvas')
  canvas.width = rawWidth
  canvas.height = rawHeight
  const context = canvas.getContext('2d')
  if (!context) return null

  objects.forEach((object) => drawObjectForBucketFill(context, object, { x: minX, y: minY }))

  const seedX = Math.floor(pointerPosition.x - minX)
  const seedY = Math.floor(pointerPosition.y - minY)
  if (seedX < 0 || seedX >= rawWidth || seedY < 0 || seedY >= rawHeight) return null

  const sourceImageData = context.getImageData(0, 0, rawWidth, rawHeight)
  const sourcePixels = sourceImageData.data
  const seedPixelIndex = seedY * rawWidth + seedX
  const seedPixelOffset = seedPixelIndex * 4
  const targetColor = {
    red: sourcePixels[seedPixelOffset],
    green: sourcePixels[seedPixelOffset + 1],
    blue: sourcePixels[seedPixelOffset + 2],
    alpha: sourcePixels[seedPixelOffset + 3],
  }
  const isTransparentTarget = targetColor.alpha <= BUCKET_FILL_ALPHA_TOLERANCE
  const barrierPixels = isTransparentTarget
    ? createDilatedBarrierPixels(sourcePixels, rawWidth, rawHeight)
    : null
  const fillColor = parseHexColor(color)
  const fillCanvas = document.createElement('canvas')
  fillCanvas.width = rawWidth
  fillCanvas.height = rawHeight
  const fillContext = fillCanvas.getContext('2d')
  if (!fillContext) return null

  const fillImageData = fillContext.createImageData(rawWidth, rawHeight)
  const fillPixels = fillImageData.data
  const visited = new Uint8Array(rawWidth * rawHeight)
  const pending = [seedPixelIndex]
  let filledPixelCount = 0
  let touchesBoundary = false

  while (pending.length > 0) {
    const currentPixelIndex = pending.pop()
    if (currentPixelIndex === undefined || visited[currentPixelIndex] === 1) continue
    visited[currentPixelIndex] = 1

    const pixelOffset = currentPixelIndex * 4
    if (barrierPixels?.[currentPixelIndex] === 1) continue
    if (!isPixelMatchingTarget(sourcePixels, pixelOffset, targetColor)) continue

    const x = currentPixelIndex % rawWidth
    const y = Math.floor(currentPixelIndex / rawWidth)
    if (x === 0 || y === 0 || x === rawWidth - 1 || y === rawHeight - 1) {
      touchesBoundary = true
    }

    fillPixels[pixelOffset] = fillColor.red
    fillPixels[pixelOffset + 1] = fillColor.green
    fillPixels[pixelOffset + 2] = fillColor.blue
    fillPixels[pixelOffset + 3] = 255
    filledPixelCount += 1

    if (x > 0) pending.push(currentPixelIndex - 1)
    if (x < rawWidth - 1) pending.push(currentPixelIndex + 1)
    if (y > 0) pending.push(currentPixelIndex - rawWidth)
    if (y < rawHeight - 1) pending.push(currentPixelIndex + rawWidth)
  }

  if (filledPixelCount === 0 || touchesBoundary) return null

  for (let dilationPass = 0; dilationPass < BUCKET_FILL_DILATION_PASSES; dilationPass++) {
    const newlyFilledIndexes: number[] = []

    for (let pixelIndex = 0; pixelIndex < rawWidth * rawHeight; pixelIndex++) {
      const pixelOffset = pixelIndex * 4
      if (fillPixels[pixelOffset + 3] === 255) continue

      const x = pixelIndex % rawWidth
      const y = Math.floor(pixelIndex / rawWidth)
      let filledNeighborCount = 0

      if (x > 0 && fillPixels[(pixelIndex - 1) * 4 + 3] === 255) {
        filledNeighborCount += 1
      }
      if (x < rawWidth - 1 && fillPixels[(pixelIndex + 1) * 4 + 3] === 255) {
        filledNeighborCount += 1
      }
      if (y > 0 && fillPixels[(pixelIndex - rawWidth) * 4 + 3] === 255) {
        filledNeighborCount += 1
      }
      if (y < rawHeight - 1 && fillPixels[(pixelIndex + rawWidth) * 4 + 3] === 255) {
        filledNeighborCount += 1
      }

      if (filledNeighborCount === 0) continue

      const sourceAlpha = sourcePixels[pixelOffset + 3]
      const isHaloCandidate = isTransparentTarget
        ? sourceAlpha < 255 || filledNeighborCount >= 3
        : Math.abs(sourcePixels[pixelOffset] - targetColor.red) <= BUCKET_FILL_DILATION_COLOR_TOLERANCE &&
          Math.abs(sourcePixels[pixelOffset + 1] - targetColor.green) <= BUCKET_FILL_DILATION_COLOR_TOLERANCE &&
          Math.abs(sourcePixels[pixelOffset + 2] - targetColor.blue) <= BUCKET_FILL_DILATION_COLOR_TOLERANCE

      if (isHaloCandidate && barrierPixels?.[pixelIndex] !== 1) {
        newlyFilledIndexes.push(pixelIndex)
      }
    }

    if (newlyFilledIndexes.length === 0) break

    for (const dilatedPixelIndex of newlyFilledIndexes) {
      const dilatedPixelOffset = dilatedPixelIndex * 4
      fillPixels[dilatedPixelOffset] = fillColor.red
      fillPixels[dilatedPixelOffset + 1] = fillColor.green
      fillPixels[dilatedPixelOffset + 2] = fillColor.blue
      fillPixels[dilatedPixelOffset + 3] = 255
      filledPixelCount += 1
    }
  }

  fillContext.putImageData(fillImageData, 0, 0)

  return {
    id: generateId(),
    type: 'fill',
    x: minX,
    y: minY,
    width: rawWidth,
    height: rawHeight,
    color,
    imageDataUrl: fillCanvas.toDataURL('image/png'),
  }
}

interface DragSelectStart {
  x: number
  y: number
  baseSelection: string[]
}

interface TextEditorRequest {
  x: number
  y: number
  fontSize: number
  color: string
  // 기존 텍스트 객체 편집인 경우 id; 신규 생성이면 null.
  editingId: string | null
}

type LocalOperationDescriptor = Omit<InfiniteCanvasOperationRequest, 'clientOperationId'>

interface UseInfinityEventsParams {
  commitLocalChange: (
    newObjects: InfinityObject[],
    selectedIds: string[],
    operations: LocalOperationDescriptor[],
  ) => void
  silentClearSelection: () => void
  silentSetSelection: (newSelectedIds: string[]) => void
  recordSelection: (newSelectedIds: string[]) => void
  onDraftObjectChange?: (draftObject: InfinityObject | null) => void
  objectsRef: { readonly current: InfinityObject[] }
  selectedIdsRef: { readonly current: string[] }
  color: string
  strokeWidth: number
  isSpaceDownRef: { readonly current: boolean }
  isShiftDownRef: { readonly current: boolean }
  // 텍스트 편집기 열기 요청 — InfinityStageView에서 textarea overlay 마운트.
  openTextEditor: (request: TextEditorRequest) => void
  canEditObject?: (id: string) => boolean
  onBlockedObjectEdit?: (id: string) => void
  // Layer 노드 ref들 — mousemove마다 React 리렌더 없이 직접 갱신.
  currentPenLineRef: React.RefObject<Konva.Line | null>
  currentEraserLineRef: React.RefObject<Konva.Line | null>
  previewRectRef: React.RefObject<Konva.Rect | null>
  previewEllipseRef: React.RefObject<Konva.Ellipse | null>
  cursorPreviewRef: React.RefObject<Konva.Circle | null>
  selectionBoxRef: React.RefObject<Konva.Rect | null>
  stageRef: React.RefObject<Konva.Stage | null>
}

export function useInfinityEvents({
  commitLocalChange,
  silentClearSelection,
  silentSetSelection,
  recordSelection,
  onDraftObjectChange,
  objectsRef,
  selectedIdsRef,
  color,
  strokeWidth,
  isSpaceDownRef,
  isShiftDownRef,
  openTextEditor,
  canEditObject,
  onBlockedObjectEdit,
  currentPenLineRef,
  currentEraserLineRef,
  previewRectRef,
  previewEllipseRef,
  cursorPreviewRef,
  selectionBoxRef,
  stageRef,
}: UseInfinityEventsParams) {
  const currentLineRef = useRef<InfinityLine | null>(null)
  const previewShapeRef = useRef<InfinityShape | null>(null)
  const hoveredObjectIdsRef = useRef<Set<string>>(new Set())
  const isDrawingRef = useRef<boolean>(false)
  const startPosRef = useRef<{ x: number; y: number } | null>(null)
  const dragSelectStartRef = useRef<DragSelectStart | null>(null)
  const dragPreviewSelectedIdsRef = useRef<string[]>([])

  const canEdit = (id: string) => canEditObject?.(id) ?? true

  const blockEdit = (id: string) => {
    onBlockedObjectEdit?.(id)
  }

  // ── Konva imperative 헬퍼 ────────────────────────────────────────────────────

  const showCurrentLine = (line: InfinityLine) => {
    const node = line.isEraser
      ? currentEraserLineRef.current
      : currentPenLineRef.current
    if (!node) return
    node.points(flattenPoints(line.points))
    node.stroke(line.isEraser ? 'rgba(0,0,0,1)' : line.color)
    node.strokeWidth(line.strokeWidth)
    node.visible(line.points.length > 1)
    node.getLayer()?.batchDraw()
  }

  const hideCurrentLines = () => {
    const pen = currentPenLineRef.current
    const eraser = currentEraserLineRef.current
    if (pen) {
      pen.points([])
      pen.visible(false)
    }
    if (eraser) {
      eraser.points([])
      eraser.visible(false)
    }
    pen?.getLayer()?.batchDraw()
    eraser?.getLayer()?.batchDraw()
  }

  const showPreviewShape = (shape: InfinityShape) => {
    if (shape.type === 'rect') {
      const node = previewRectRef.current
      if (!node) return
      node.position({ x: shape.x, y: shape.y })
      node.width(shape.width)
      node.height(shape.height)
      node.stroke(shape.color)
      node.strokeWidth(shape.strokeWidth)
      node.visible(true)
      node.getLayer()?.batchDraw()
    } else {
      const node = previewEllipseRef.current
      if (!node) return
      const radiusX = Math.abs(shape.width / 2)
      const radiusY = Math.abs(shape.height / 2)
      node.position({ x: shape.x + shape.width / 2, y: shape.y + shape.height / 2 })
      node.radiusX(radiusX)
      node.radiusY(radiusY)
      node.stroke(shape.color)
      node.strokeWidth(shape.strokeWidth)
      node.visible(true)
      node.getLayer()?.batchDraw()
    }
  }

  const hidePreviewShapes = () => {
    const rect = previewRectRef.current
    const ellipse = previewEllipseRef.current
    if (rect) rect.visible(false)
    if (ellipse) ellipse.visible(false)
    rect?.getLayer()?.batchDraw()
  }

  // ── 마우스 커서 미리보기 ─────────────────────────────────────────────────────
  // 도구별 정책:
  //   pen          → fill = 사용자 색, stroke = black
  //   eraser       → fill = white, stroke = black
  //   select-eraser→ fill = white, stroke = black
  //   그 외        → 숨김
  const updateCursorForTool = (
    pos: { x: number; y: number } | null,
    tool: InfinityToolKey,
  ) => {
    const node = cursorPreviewRef.current
    if (!node) return
    if (!pos) {
      node.visible(false)
      node.getLayer()?.batchDraw()
      return
    }
    if (tool === 'pen') {
      node.fill(color)
      node.stroke('black')
      node.radius(Math.max(strokeWidth / 2, 1))
      node.position(pos)
      node.visible(true)
    } else if (tool === 'eraser' || tool === 'select-eraser') {
      node.fill('white')
      node.stroke('black')
      node.radius(Math.max(strokeWidth / 2, 4))
      node.position(pos)
      node.visible(true)
    } else {
      node.visible(false)
    }
    node.getLayer()?.batchDraw()
  }

  const hideCursor = () => {
    const node = cursorPreviewRef.current
    if (!node) return
    node.visible(false)
    node.getLayer()?.batchDraw()
  }

  // ── 드래그 선택 박스 ────────────────────────────────────────────────────────
  const showSelectionBox = (rect: { x: number; y: number; width: number; height: number }) => {
    const node = selectionBoxRef.current
    if (!node) return
    node.position({ x: rect.x, y: rect.y })
    node.width(rect.width)
    node.height(rect.height)
    node.visible(true)
    node.getLayer()?.batchDraw()
  }
  const hideSelectionBox = () => {
    const node = selectionBoxRef.current
    if (!node) return
    node.visible(false)
    node.getLayer()?.batchDraw()
  }

  // ── select-eraser hover opacity ──────────────────────────────────────────────
  const applyHoverOpacity = (newHoverIds: Set<string>) => {
    const stage = stageRef.current
    if (!stage) return
    const previousIds = hoveredObjectIdsRef.current
    let layerToDraw: Konva.Layer | null = null

    for (const id of previousIds) {
      if (newHoverIds.has(id)) continue
      const node = stage.findOne(`#${id}`)
      if (node) {
        node.opacity(1)
        layerToDraw = node.getLayer()
      }
    }
    for (const id of newHoverIds) {
      if (previousIds.has(id)) continue
      const node = stage.findOne(`#${id}`)
      if (node) {
        node.opacity(0.3)
        layerToDraw = node.getLayer()
      }
    }
    hoveredObjectIdsRef.current = newHoverIds
    layerToDraw?.batchDraw()
  }

  const resetDrawingState = () => {
    silentClearSelection()
    currentLineRef.current = null
    previewShapeRef.current = null
    dragSelectStartRef.current = null
    dragPreviewSelectedIdsRef.current = []
    hideCurrentLines()
    hidePreviewShapes()
    hideCursor()
    hideSelectionBox()
    applyHoverOpacity(new Set())
    isDrawingRef.current = false
    onDraftObjectChange?.(null)
  }

  const cleanupAfterNextPaint = (cleanup: () => void) => {
    window.requestAnimationFrame(cleanup)
  }

  const containsRect = (
    outerRect: { x: number; y: number; width: number; height: number },
    innerRect: { x: number; y: number; width: number; height: number },
  ): boolean =>
    innerRect.x >= outerRect.x &&
    innerRect.y >= outerRect.y &&
    innerRect.x + innerRect.width <= outerRect.x + outerRect.width &&
    innerRect.y + innerRect.height <= outerRect.y + outerRect.height

  const getContainedSelectableIds = (
    stage: Konva.Stage,
    box: { x: number; y: number; width: number; height: number },
    baseSelection: string[],
  ) => {
    const baseSet = new Set(baseSelection)
    const hitIds: string[] = [...baseSelection]
    for (const object of objectsRef.current) {
      if (!canEdit(object.id)) continue
      const objectNode = stage.findOne(`#${object.id}`)
      if (!objectNode) continue
      const rect = objectNode.getClientRect({ relativeTo: stage })
      if (containsRect(box, rect) && !baseSet.has(object.id)) {
        hitIds.push(object.id)
      }
    }
    return hitIds
  }

  const isSameSelection = (firstIds: string[], secondIds: string[]) =>
    firstIds.length === secondIds.length && firstIds.every((id, index) => id === secondIds[index])

  // ── Stage 이벤트 핸들러 ──────────────────────────────────────────────────────

  const onStageMouseDown = (
    stage: Konva.Stage,
    toolSnapshot: InfinityToolKey,
    targetIsStage: boolean,
  ) => {
    if (isSpaceDownRef.current) return
    if (toolSnapshot === 'hand') {
      hideCursor()
      return
    }

    const pos = stage.getRelativePointerPosition()
    if (!pos) return

    isDrawingRef.current = true
    startPosRef.current = pos

    if (toolSnapshot === 'pen' || toolSnapshot === 'eraser') {
      const newLine: InfinityLine = {
        id: generateId(),
        type: 'line',
        color,
        strokeWidth,
        points: [pos],
        isEraser: toolSnapshot === 'eraser',
      }
      currentLineRef.current = newLine
      onDraftObjectChange?.(toolSnapshot === 'eraser' ? null : newLine)
    } else if (toolSnapshot === 'bucket') {
      isDrawingRef.current = false
    } else if (toolSnapshot === 'select-eraser') {
      applyHoverOpacity(new Set())
    } else if (isShapeTool(toolSnapshot)) {
      const fillTool = isFillTool(toolSnapshot)
      const newShape: InfinityShape = {
        id: generateId(),
        type: shapeTypeOfTool(toolSnapshot),
        x: pos.x,
        y: pos.y,
        width: 0,
        height: 0,
        color,
        strokeWidth,
        ...(fillTool ? { fill: color } : {}),
      }
      previewShapeRef.current = newShape
      onDraftObjectChange?.(newShape)
    } else if (toolSnapshot === 'select') {
      // 빈 배경(target === stage)에서 드래그 시작 시에만 selection box 활성화.
      if (targetIsStage) {
        dragSelectStartRef.current = {
          x: pos.x,
          y: pos.y,
          baseSelection: isShiftDownRef.current
            ? [...selectedIdsRef.current]
            : [],
        }
        dragPreviewSelectedIdsRef.current = dragSelectStartRef.current.baseSelection
      } else {
        // 객체 위에서 mousedown → drag select 비활성. 이동/선택은 객체 핸들러에서.
        isDrawingRef.current = false
      }
    }
    // text 도구는 mousedown 무시 — 클릭(아래 onStageClick)에서 처리.
  }

  const onStageMouseMove = (
    stage: Konva.Stage,
    toolSnapshot: InfinityToolKey,
  ) => {
    const pos = stage.getRelativePointerPosition()

    const isStrokeDrawing = isDrawingRef.current && (toolSnapshot === 'pen' || toolSnapshot === 'eraser')
    if (!isStrokeDrawing) {
      updateCursorForTool(pos, toolSnapshot)
    }

    if (!isDrawingRef.current) return
    if (isSpaceDownRef.current) return
    if (!pos) return

    if (toolSnapshot === 'pen' || toolSnapshot === 'eraser') {
      const prev = currentLineRef.current
      if (!prev) return
      if (!shouldAppendLinePoint(prev.points.at(-1), pos)) return
      prev.points.push(pos)
      prev.points = limitLinePoints(prev.points, MAX_LINE_POINTS_PER_OBJECT)
      showCurrentLine(prev)
      onDraftObjectChange?.(prev.isEraser ? null : createDraftLine(prev))
    } else if (toolSnapshot === 'select-eraser') {
      const pointer = stage.getPointerPosition()
      if (!pointer) return
      const intersections = stage.getAllIntersections(pointer)
      const hitIds = new Set<string>(hoveredObjectIdsRef.current)
      for (const node of intersections) {
        const id = node.id()
        if (id) hitIds.add(id)
      }
      applyHoverOpacity(hitIds)
    } else if (isShapeTool(toolSnapshot)) {
      const startPos = startPosRef.current
      const prev = previewShapeRef.current
      if (!startPos || !prev) return
      const dx = pos.x - startPos.x
      const dy = pos.y - startPos.y
      let width = Math.abs(dx)
      let height = Math.abs(dy)
      if (isShiftDownRef.current) {
        const size = Math.max(width, height)
        width = size
        height = size
      }
      prev.x = dx >= 0 ? startPos.x : startPos.x - width
      prev.y = dy >= 0 ? startPos.y : startPos.y - height
      prev.width = width
      prev.height = height
      showPreviewShape(prev)
      onDraftObjectChange?.({ ...prev })
    } else if (toolSnapshot === 'select') {
      const dragStart = dragSelectStartRef.current
      if (!dragStart) return
      const x = Math.min(dragStart.x, pos.x)
      const y = Math.min(dragStart.y, pos.y)
      const width = Math.abs(pos.x - dragStart.x)
      const height = Math.abs(pos.y - dragStart.y)
      const box = { x, y, width, height }
      showSelectionBox(box)
      if (width < 3 || height < 3) return
      const nextSelectedIds = getContainedSelectableIds(stage, box, dragStart.baseSelection)
      if (!isSameSelection(dragPreviewSelectedIdsRef.current, nextSelectedIds)) {
        dragPreviewSelectedIdsRef.current = nextSelectedIds
        silentSetSelection(nextSelectedIds)
      }
    }
  }

  const onStageMouseUp = (toolSnapshot: InfinityToolKey) => {
    if (!isDrawingRef.current) return
    isDrawingRef.current = false

    if (toolSnapshot === 'pen' || toolSnapshot === 'eraser') {
      const line = currentLineRef.current
      if (line && line.points.length > 1) {
        const persistedLine = createPersistedLine(line)
        commitLocalChange([...objectsRef.current, persistedLine], selectedIdsRef.current, [
          {
            operationType: 'UPSERT_ELEMENT',
            elementId: persistedLine.id,
            element: { ...persistedLine },
          },
        ])
      }
      currentLineRef.current = null
      onDraftObjectChange?.(null)
      cleanupAfterNextPaint(hideCurrentLines)
    } else if (toolSnapshot === 'select-eraser') {
      const idsToRemove = hoveredObjectIdsRef.current
      if (idsToRemove.size > 0) {
        const blockedId = [...idsToRemove].find((id) => !canEdit(id))
        if (blockedId) {
          blockEdit(blockedId)
          applyHoverOpacity(new Set())
          startPosRef.current = null
          return
        }
        const newObjects = objectsRef.current.filter(
          (obj) => !idsToRemove.has(obj.id),
        )
        const newSelected = selectedIdsRef.current.filter(
          (id) => !idsToRemove.has(id),
        )
        commitLocalChange(
          newObjects,
          newSelected,
          [...idsToRemove].map((elementId) => ({
            operationType: 'DELETE_ELEMENT',
            elementId,
          })),
        )
      }
      applyHoverOpacity(new Set())
    } else if (isShapeTool(toolSnapshot)) {
      const shape = previewShapeRef.current
      if (shape && (Math.abs(shape.width) > 5 || Math.abs(shape.height) > 5)) {
        commitLocalChange([...objectsRef.current, shape], selectedIdsRef.current, [
          {
            operationType: 'UPSERT_ELEMENT',
            elementId: shape.id,
            element: { ...shape },
          },
        ])
      }
      previewShapeRef.current = null
      onDraftObjectChange?.(null)
      cleanupAfterNextPaint(hidePreviewShapes)
    } else if (toolSnapshot === 'select') {
      const dragStart = dragSelectStartRef.current
      const stage = stageRef.current
      if (dragStart && stage) {
        const node = selectionBoxRef.current
        const isVisible = node?.visible() ?? false
        if (isVisible) {
          const box = {
            x: node!.x(),
            y: node!.y(),
            width: node!.width(),
            height: node!.height(),
          }
          // 박스가 너무 작으면 클릭으로 간주 — 선택 변경 없이 hide만.
          if (box.width >= 3 && box.height >= 3) {
            const hitIds = getContainedSelectableIds(stage, box, dragStart.baseSelection)
            recordSelection(hitIds)
          }
        }
        hideSelectionBox()
      }
      dragSelectStartRef.current = null
      dragPreviewSelectedIdsRef.current = []
    }

    startPosRef.current = null
  }

  const onStageMouseLeave = (toolSnapshot: InfinityToolKey) => {
    if (isDrawingRef.current) {
      onStageMouseUp(toolSnapshot)
    }
    hideCursor()
  }

  const onStageClick = (
    e: Konva.KonvaEventObject<MouseEvent>,
    toolSnapshot: InfinityToolKey,
  ) => {
    const stage = e.target.getStage()
    if (!stage) return
    const targetIsStage = e.target === stage

    if (toolSnapshot === 'bucket') {
      const targetId = e.target.id()
      if (!targetIsStage && targetId) {
        onObjectClick(targetId, false, toolSnapshot)
        return
      }

      const pos = stage.getRelativePointerPosition()
      if (!pos) return
      const fillObject = createBucketFillObject({
        objects: objectsRef.current,
        pointerPosition: pos,
        color,
      })
      if (fillObject) {
        commitLocalChange([...objectsRef.current, fillObject], selectedIdsRef.current, [
          {
            operationType: 'UPSERT_ELEMENT',
            elementId: fillObject.id,
            element: { ...fillObject },
          },
        ])
      }
      return
    }

    if (toolSnapshot === 'text' && targetIsStage) {
      const pos = stage.getRelativePointerPosition()
      if (!pos) return
      openTextEditor({
        x: pos.x,
        y: pos.y,
        fontSize: INFINITY_TEXT_DEFAULT_FONT_SIZE,
        color,
        editingId: null,
      })
      return
    }

    if (toolSnapshot === 'select' && targetIsStage) {
      // 빈 배경 클릭 → silent deselect (다중 선택 중일 때만 변화).
      if (selectedIdsRef.current.length > 0) silentClearSelection()
    }
  }

  // 도형/텍스트 클릭 → 단일/다중 선택 토글, history 기록.
  const onObjectClick = (id: string, isShift: boolean, toolSnapshot: InfinityToolKey = 'select') => {
    if (!canEdit(id)) {
      blockEdit(id)
      return
    }

    if (toolSnapshot === 'bucket') {
      const nextObjects = objectsRef.current.map((object) =>
        object.id === id ? recolorObject(object, color) : object,
      )
      const nextSelectedIds = selectedIdsRef.current.includes(id) ? selectedIdsRef.current : [id]
      const updatedObject = nextObjects.find((object) => object.id === id)
      if (!updatedObject) return
      commitLocalChange(nextObjects, nextSelectedIds, [
        {
          operationType: 'UPSERT_ELEMENT',
          elementId: updatedObject.id,
          element: { ...updatedObject },
        },
      ])
      return
    }

    const current = selectedIdsRef.current
    let next: string[]
    if (isShift) {
      next = current.includes(id)
        ? current.filter((selectedId) => selectedId !== id)
        : [...current, id]
    } else {
      if (current.length === 1 && current[0] === id) return
      next = [id]
    }
    recordSelection(next)
  }

  const onObjectDragEnd = (id: string, x: number, y: number) => {
    if (!canEdit(id)) {
      blockEdit(id)
      return
    }
    const newObjects = objectsRef.current.map((obj) => {
      if (obj.id !== id) return obj
      if (obj.type === 'line') return obj
      return { ...obj, x, y }
    })
    const updatedObject = newObjects.find((object) => object.id === id)
    if (!updatedObject) return
    commitLocalChange(newObjects, selectedIdsRef.current, [
      {
        operationType: 'UPSERT_ELEMENT',
        elementId: updatedObject.id,
        element: { ...updatedObject },
      },
    ])
  }

  const onShapeTransformEnd = (
    id: string,
    x: number,
    y: number,
    width: number,
    height: number,
    rotation: number,
  ) => {
    if (!canEdit(id)) {
      blockEdit(id)
      return
    }
    const newObjects = objectsRef.current.map((obj) => {
      if (obj.id !== id) return obj
      if (obj.type === 'rect' || obj.type === 'ellipse') {
        return { ...obj, x, y, width, height, rotation }
      }
      return obj
    })
    const updatedObject = newObjects.find((object) => object.id === id)
    if (!updatedObject) return
    commitLocalChange(newObjects, selectedIdsRef.current, [
      {
        operationType: 'UPSERT_ELEMENT',
        elementId: updatedObject.id,
        element: { ...updatedObject },
      },
    ])
  }

  const onTextTransformEnd = (
    id: string,
    x: number,
    y: number,
    rotation: number,
  ) => {
    if (!canEdit(id)) {
      blockEdit(id)
      return
    }
    const newObjects = objectsRef.current.map((obj) => {
      if (obj.id !== id) return obj
      if (obj.type === 'text') {
        return { ...obj, x, y, rotation }
      }
      return obj
    })
    const updatedObject = newObjects.find((object) => object.id === id)
    if (!updatedObject) return
    commitLocalChange(newObjects, selectedIdsRef.current, [
      {
        operationType: 'UPSERT_ELEMENT',
        elementId: updatedObject.id,
        element: { ...updatedObject },
      },
    ])
  }

  // 텍스트 객체 더블 클릭 → 편집 모드 진입.
  const onTextDblClick = (id: string) => {
    if (!canEdit(id)) {
      blockEdit(id)
      return
    }
    const target = objectsRef.current.find((obj) => obj.id === id)
    if (!target || target.type !== 'text') return
    const text = target as InfinityText
    openTextEditor({
      x: text.x,
      y: text.y,
      fontSize: text.fontSize,
      color: text.color,
      editingId: text.id,
    })
  }

  // ── z-index 단축키 처리 — drawing 레이어가 호출 ────────────────────────────
  // direction: +1 = forward, -1 = backward
  const shiftSelectedZIndex = (direction: 1 | -1) => {
    const ids = selectedIdsRef.current
    if (ids.length === 0) return
    const blockedId = ids.find((id) => !canEdit(id))
    if (blockedId) {
      blockEdit(blockedId)
      return
    }
    const objects = [...objectsRef.current]
    // forward는 뒤에서부터, backward는 앞에서부터 처리해 인덱스 충돌 방지.
    const orderedIndices = ids
      .map((id) => objects.findIndex((obj) => obj.id === id))
      .filter((i) => i >= 0)
      .sort((a, b) => (direction > 0 ? b - a : a - b))

    let changed = false
    for (const i of orderedIndices) {
      const swapWith = i + direction
      if (swapWith < 0 || swapWith >= objects.length) continue
      if (ids.includes(objects[swapWith].id)) continue
      ;[objects[i], objects[swapWith]] = [objects[swapWith], objects[i]]
      changed = true
    }
    if (changed) {
      commitLocalChange(
        objects,
        ids,
        ids
          .map((id) => objects.find((object) => object.id === id))
          .filter((object): object is InfinityObject => Boolean(object))
          .map((object) => ({
            operationType: 'UPSERT_ELEMENT',
            elementId: object.id,
            element: { ...object },
          })),
      )
    }
  }

  return {
    isDrawingRef,
    resetDrawingState,
    onStageMouseDown,
    onStageMouseMove,
    onStageMouseUp,
    onStageMouseLeave,
    onStageClick,
    onObjectClick,
    onObjectDragEnd,
    onShapeTransformEnd,
    onTextTransformEnd,
    onTextDblClick,
    shiftSelectedZIndex,
  } as const
}
