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
  INFINITY_TEXT_DEFAULT_COLOR,
  INFINITY_TEXT_DEFAULT_FONT_FAMILY,
  INFINITY_TEXT_DEFAULT_FONT_SIZE,
} from '../constants'
import {
  getInfinityObjectLayerIndex,
  normalizeInfinityObjectLayerIndexes,
} from '../infinityObjectUtils'

function generateId(): string {
  return Math.random().toString(36).slice(2, 9)
}

function flattenPoints(points: { x: number; y: number }[]): number[] {
  return points.flatMap((p) => [p.x, p.y])
}

const MIN_LINE_POINT_DISTANCE = 0
const MAX_LINE_POINTS_PER_OBJECT = 5200
const MAX_DRAFT_LINE_POINTS = 600
const BUCKET_FILL_PADDING = 96
const BUCKET_FILL_MAX_SIZE = 1600
const BUCKET_FILL_ALPHA_TOLERANCE = 16
const BUCKET_FILL_COLOR_TOLERANCE = 12
const BUCKET_FILL_BARRIER_DILATION_PASSES = 0
const BUCKET_FILL_DILATION_PASSES = 6
const BUCKET_FILL_DILATION_COLOR_TOLERANCE = 96
const BUCKET_FILL_HIT_PADDING = 20
const SHAPE_PREVIEW_MIN_DELTA = 0.5
const SELECTION_BOX_HIT_PADDING = 8

interface Bounds {
  x: number
  y: number
  width: number
  height: number
}

interface FillableObjectEntry {
  object: InfinityObject
  bounds: Bounds
}

function shouldAppendLinePoint(
  previousPoint: { x: number; y: number } | undefined,
  nextPoint: { x: number; y: number },
) {
  if (!previousPoint) return true
  const distanceX = nextPoint.x - previousPoint.x
  const distanceY = nextPoint.y - previousPoint.y
  if (distanceX === 0 && distanceY === 0) return false
  if (MIN_LINE_POINT_DISTANCE <= 0) return true
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
  if (object.type === 'image') {
    return object
  }

  return { ...object, color }
}

function getDragDeltaFromObject(object: InfinityObject, x: number, y: number) {
  if (object.type === 'line') {
    return { x, y }
  }

  return {
    x: x - object.x,
    y: y - object.y,
  }
}

function moveObjectByDelta(object: InfinityObject, deltaX: number, deltaY: number): InfinityObject {
  if (object.type === 'line') {
    return {
      ...object,
      points: object.points.map((point) => ({
        x: point.x + deltaX,
        y: point.y + deltaY,
      })),
    }
  }

  return {
    ...object,
    x: object.x + deltaX,
    y: object.y + deltaY,
  }
}

function moveSelectedObjectsByLayer(objects: InfinityObject[], selectedIds: string[], direction: 1 | -1) {
  const selectedIdSet = new Set(selectedIds)
  const currentObjects = normalizeInfinityObjectLayerIndexes(objects)
  const currentLayerIndexById = new Map(currentObjects.map((object) => [object.id, object.zIndex]))
  const orderedIndices = selectedIds
    .map((id) => currentObjects.findIndex((object) => object.id === id))
    .filter((layerIndex) => layerIndex >= 0)
    .sort((firstIndex, secondIndex) => (direction > 0 ? secondIndex - firstIndex : firstIndex - secondIndex))

  let changed = false
  for (const layerIndex of orderedIndices) {
    const swapWithIndex = layerIndex + direction
    if (swapWithIndex < 0 || swapWithIndex >= currentObjects.length) continue
    if (selectedIdSet.has(currentObjects[swapWithIndex].id)) continue
    ;[currentObjects[layerIndex], currentObjects[swapWithIndex]] = [
      currentObjects[swapWithIndex],
      currentObjects[layerIndex],
    ]
    changed = true
  }

  if (!changed) return null

  const hasUnstableLayerIndex = objects.some(
    (object, fallbackIndex) => object.zIndex !== getInfinityObjectLayerIndex(object, fallbackIndex),
  )
  const newObjects = currentObjects.map((object, layerIndex) => ({
    ...object,
    zIndex: layerIndex,
  }))
  const updatedObjects = hasUnstableLayerIndex
    ? newObjects
    : newObjects.filter((object) => currentLayerIndexById.get(object.id) !== object.zIndex)

  return { newObjects, updatedObjects }
}

function getObjectBounds(object: InfinityObject): Bounds | null {
  if (object.type === 'fill' || object.type === 'rect' || object.type === 'ellipse' || object.type === 'image') {
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
  let minX = object.points[0].x
  let maxX = object.points[0].x
  let minY = object.points[0].y
  let maxY = object.points[0].y
  for (let pointIndex = 1; pointIndex < object.points.length; pointIndex++) {
    const point = object.points[pointIndex]
    if (point.x < minX) minX = point.x
    if (point.x > maxX) maxX = point.x
    if (point.y < minY) minY = point.y
    if (point.y > maxY) maxY = point.y
  }
  const padding = Math.max(object.strokeWidth, 8)

  return {
    x: minX - padding,
    y: minY - padding,
    width: Math.max(maxX - minX + padding * 2, padding * 2),
    height: Math.max(maxY - minY + padding * 2, padding * 2),
  }
}

function containsPoint(
  bounds: Bounds,
  point: { x: number; y: number },
  padding = 0,
) {
  return (
    point.x >= bounds.x - padding &&
    point.x <= bounds.x + bounds.width + padding &&
    point.y >= bounds.y - padding &&
    point.y <= bounds.y + bounds.height + padding
  )
}

function intersectsBounds(firstBounds: Bounds, secondBounds: Bounds, padding = 0) {
  return (
    firstBounds.x - padding <= secondBounds.x + secondBounds.width &&
    firstBounds.x + firstBounds.width + padding >= secondBounds.x &&
    firstBounds.y - padding <= secondBounds.y + secondBounds.height &&
    firstBounds.y + firstBounds.height + padding >= secondBounds.y
  )
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
      for (let pointIndex = 1; pointIndex < object.points.length; pointIndex++) {
        const point = object.points[pointIndex]
        context.lineTo(point.x, point.y)
      }
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
  const fillableEntries: FillableObjectEntry[] = []
  const candidateEntries: FillableObjectEntry[] = []
  let candidateMinX = pointerPosition.x
  let candidateMinY = pointerPosition.y
  let candidateMaxX = pointerPosition.x
  let candidateMaxY = pointerPosition.y

  for (const object of objects) {
    if (object.type === 'fill' || object.type === 'text') continue
    const bounds = getObjectBounds(object)
    if (!bounds) continue
    const entry = { object, bounds }
    fillableEntries.push(entry)

    if (!containsPoint(bounds, pointerPosition, BUCKET_FILL_HIT_PADDING)) continue
    candidateEntries.push(entry)
    candidateMinX = Math.min(candidateMinX, bounds.x)
    candidateMinY = Math.min(candidateMinY, bounds.y)
    candidateMaxX = Math.max(candidateMaxX, bounds.x + bounds.width)
    candidateMaxY = Math.max(candidateMaxY, bounds.y + bounds.height)
  }

  if (candidateEntries.length === 0) return null

  const minX = Math.floor(candidateMinX - BUCKET_FILL_PADDING)
  const minY = Math.floor(candidateMinY - BUCKET_FILL_PADDING)
  const maxX = Math.ceil(candidateMaxX + BUCKET_FILL_PADDING)
  const maxY = Math.ceil(candidateMaxY + BUCKET_FILL_PADDING)
  const rawWidth = maxX - minX
  const rawHeight = maxY - minY
  if (rawWidth <= 0 || rawHeight <= 0 || rawWidth > BUCKET_FILL_MAX_SIZE || rawHeight > BUCKET_FILL_MAX_SIZE) {
    return null
  }

  const bucketBounds = { x: minX, y: minY, width: rawWidth, height: rawHeight }
  const affectedEntries = fillableEntries.filter((entry) =>
    intersectsBounds(entry.bounds, bucketBounds, BUCKET_FILL_HIT_PADDING),
  )
  const canvas = document.createElement('canvas')
  canvas.width = rawWidth
  canvas.height = rawHeight
  const context = canvas.getContext('2d', { willReadFrequently: true })
  if (!context) return null

  affectedEntries.forEach((entry) => drawObjectForBucketFill(context, entry.object, { x: minX, y: minY }))

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
  const selectedFillColor = parseHexColor(color)
  if (
    targetColor.alpha > BUCKET_FILL_ALPHA_TOLERANCE &&
    Math.abs(targetColor.red - selectedFillColor.red) <= BUCKET_FILL_COLOR_TOLERANCE &&
    Math.abs(targetColor.green - selectedFillColor.green) <= BUCKET_FILL_COLOR_TOLERANCE &&
    Math.abs(targetColor.blue - selectedFillColor.blue) <= BUCKET_FILL_COLOR_TOLERANCE
  ) {
    return null
  }

  const isTransparentTarget = targetColor.alpha <= BUCKET_FILL_ALPHA_TOLERANCE
  const barrierPixels = isTransparentTarget && BUCKET_FILL_BARRIER_DILATION_PASSES > 0
    ? createDilatedBarrierPixels(sourcePixels, rawWidth, rawHeight)
    : null
  const fillCanvas = document.createElement('canvas')
  fillCanvas.width = rawWidth
  fillCanvas.height = rawHeight
  const fillContext = fillCanvas.getContext('2d')
  if (!fillContext) return null

  const fillImageData = fillContext.createImageData(rawWidth, rawHeight)
  const fillPixels = fillImageData.data
  const pixelCount = rawWidth * rawHeight
  const visited = new Uint8Array(pixelCount)
  const pending = new Int32Array(pixelCount)
  let pendingCount = 0
  let filledPixelCount = 0
  let touchesBoundary = false
  let filledMinX = rawWidth
  let filledMinY = rawHeight
  let filledMaxX = 0
  let filledMaxY = 0

  const enqueuePixel = (pixelIndex: number) => {
    if (visited[pixelIndex] === 1) return
    visited[pixelIndex] = 1
    pending[pendingCount] = pixelIndex
    pendingCount += 1
  }

  enqueuePixel(seedPixelIndex)

  while (pendingCount > 0) {
    pendingCount -= 1
    const currentPixelIndex = pending[pendingCount]
    const pixelOffset = currentPixelIndex * 4
    if (barrierPixels?.[currentPixelIndex] === 1) continue
    if (!isPixelMatchingTarget(sourcePixels, pixelOffset, targetColor)) continue

    const x = currentPixelIndex % rawWidth
    const y = Math.floor(currentPixelIndex / rawWidth)
    if (x === 0 || y === 0 || x === rawWidth - 1 || y === rawHeight - 1) {
      touchesBoundary = true
    }

    fillPixels[pixelOffset] = selectedFillColor.red
    fillPixels[pixelOffset + 1] = selectedFillColor.green
    fillPixels[pixelOffset + 2] = selectedFillColor.blue
    fillPixels[pixelOffset + 3] = 255
    filledPixelCount += 1
    if (x < filledMinX) filledMinX = x
    if (y < filledMinY) filledMinY = y
    if (x > filledMaxX) filledMaxX = x
    if (y > filledMaxY) filledMaxY = y

    if (x > 0) enqueuePixel(currentPixelIndex - 1)
    if (x < rawWidth - 1) enqueuePixel(currentPixelIndex + 1)
    if (y > 0) enqueuePixel(currentPixelIndex - rawWidth)
    if (y < rawHeight - 1) enqueuePixel(currentPixelIndex + rawWidth)
  }

  if (filledPixelCount === 0 || touchesBoundary) return null

  for (let dilationPass = 0; dilationPass < BUCKET_FILL_DILATION_PASSES; dilationPass++) {
    const newlyFilledIndexes: number[] = []
    const scanMinX = Math.max(0, filledMinX - 1)
    const scanMinY = Math.max(0, filledMinY - 1)
    const scanMaxX = Math.min(rawWidth - 1, filledMaxX + 1)
    const scanMaxY = Math.min(rawHeight - 1, filledMaxY + 1)

    for (let y = scanMinY; y <= scanMaxY; y++) {
      const rowOffset = y * rawWidth
      for (let x = scanMinX; x <= scanMaxX; x++) {
        const pixelIndex = rowOffset + x
        const pixelOffset = pixelIndex * 4
        if (fillPixels[pixelOffset + 3] === 255) continue

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
    }

    if (newlyFilledIndexes.length === 0) break

    for (const dilatedPixelIndex of newlyFilledIndexes) {
      const dilatedPixelOffset = dilatedPixelIndex * 4
      fillPixels[dilatedPixelOffset] = selectedFillColor.red
      fillPixels[dilatedPixelOffset + 1] = selectedFillColor.green
      fillPixels[dilatedPixelOffset + 2] = selectedFillColor.blue
      fillPixels[dilatedPixelOffset + 3] = 255
      filledPixelCount += 1
      const x = dilatedPixelIndex % rawWidth
      const y = Math.floor(dilatedPixelIndex / rawWidth)
      if (x < filledMinX) filledMinX = x
      if (y < filledMinY) filledMinY = y
      if (x > filledMaxX) filledMaxX = x
      if (y > filledMaxY) filledMaxY = y
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
  fontFamily: string
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
  onDraftObjectChange?: (draftObject: InfinityObject | InfinityObject[] | null) => void
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
  const pendingPreviewShapeRef = useRef<InfinityShape | null>(null)
  const shapePreviewFrameRef = useRef<number | null>(null)
  const lastShapePreviewBoundsRef = useRef<Bounds | null>(null)
  const hoveredObjectIdsRef = useRef<Set<string>>(new Set())
  const isDrawingRef = useRef<boolean>(false)
  const startPosRef = useRef<{ x: number; y: number } | null>(null)
  const dragSelectStartRef = useRef<DragSelectStart | null>(null)
  const dragPreviewSelectedIdsRef = useRef<string[]>([])

  const canEdit = (id: string) => canEditObject?.(id) ?? true

  const blockEdit = (id: string) => {
    onBlockedObjectEdit?.(id)
  }

  const resetLineNodeOffsets = (ids: string[]) => {
    const stage = stageRef.current
    if (!stage) return

    let shouldDraw = false
    for (const elementId of ids) {
      const object = objectsRef.current.find((item) => item.id === elementId)
      if (object?.type !== 'line') continue
      const node = stage.findOne(`#${elementId}`)
      if (!node) continue
      node.position({ x: 0, y: 0 })
      shouldDraw = true
    }

    if (shouldDraw) stage.batchDraw()
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

  const rememberShapePreviewBounds = (shape: InfinityShape) => {
    lastShapePreviewBoundsRef.current = {
      x: shape.x,
      y: shape.y,
      width: shape.width,
      height: shape.height,
    }
  }

  const shouldRefreshShapePreview = (shape: InfinityShape) => {
    const previousBounds = lastShapePreviewBoundsRef.current
    if (!previousBounds) return true

    return (
      Math.abs(previousBounds.x - shape.x) >= SHAPE_PREVIEW_MIN_DELTA ||
      Math.abs(previousBounds.y - shape.y) >= SHAPE_PREVIEW_MIN_DELTA ||
      Math.abs(previousBounds.width - shape.width) >= SHAPE_PREVIEW_MIN_DELTA ||
      Math.abs(previousBounds.height - shape.height) >= SHAPE_PREVIEW_MIN_DELTA
    )
  }

  const flushPendingPreviewShape = () => {
    if (shapePreviewFrameRef.current !== null) {
      window.cancelAnimationFrame(shapePreviewFrameRef.current)
      shapePreviewFrameRef.current = null
    }
    const pendingShape = pendingPreviewShapeRef.current
    pendingPreviewShapeRef.current = null
    if (!pendingShape) return

    showPreviewShape(pendingShape)
    rememberShapePreviewBounds(pendingShape)
    onDraftObjectChange?.({ ...pendingShape })
  }

  const schedulePreviewShape = (shape: InfinityShape) => {
    if (!shouldRefreshShapePreview(shape) && shapePreviewFrameRef.current === null) return
    pendingPreviewShapeRef.current = shape
    if (shapePreviewFrameRef.current !== null) return
    shapePreviewFrameRef.current = window.requestAnimationFrame(flushPendingPreviewShape)
  }

  const cancelPendingPreviewShape = () => {
    if (shapePreviewFrameRef.current !== null) {
      window.cancelAnimationFrame(shapePreviewFrameRef.current)
      shapePreviewFrameRef.current = null
    }
    pendingPreviewShapeRef.current = null
    lastShapePreviewBoundsRef.current = null
  }

  const hidePreviewShapes = () => {
    cancelPendingPreviewShape()
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
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => {
        window.setTimeout(cleanup, 120)
      })
    })
  }

  const expandRect = (
    rect: { x: number; y: number; width: number; height: number },
    padding: number,
  ) => ({
    x: rect.x - padding,
    y: rect.y - padding,
    width: rect.width + padding * 2,
    height: rect.height + padding * 2,
  })

  const intersectsRect = (
    firstRect: { x: number; y: number; width: number; height: number },
    secondRect: { x: number; y: number; width: number; height: number },
  ): boolean =>
    firstRect.x <= secondRect.x + secondRect.width &&
    firstRect.x + firstRect.width >= secondRect.x &&
    firstRect.y <= secondRect.y + secondRect.height &&
    firstRect.y + firstRect.height >= secondRect.y

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
      const rect = expandRect(
        objectNode.getClientRect({ relativeTo: stage }),
        SELECTION_BOX_HIT_PADDING,
      )
      if (intersectsRect(box, rect) && !baseSet.has(object.id)) {
        hitIds.push(object.id)
      }
    }
    return hitIds
  }

  const isSameSelection = (firstIds: string[], secondIds: string[]) =>
    firstIds.length === secondIds.length && firstIds.every((id, index) => id === secondIds[index])

  const openExistingTextEditor = (text: InfinityText) => {
    openTextEditor({
      x: text.x,
      y: text.y,
      fontSize: text.fontSize,
      color: text.color,
      fontFamily: text.fontFamily ?? INFINITY_TEXT_DEFAULT_FONT_FAMILY,
      editingId: text.id,
    })
  }

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
      hideCursor()
      const newLine: InfinityLine = {
        id: generateId(),
        type: 'line',
        color,
        strokeWidth,
        points: [pos],
        isEraser: toolSnapshot === 'eraser',
      }
      currentLineRef.current = newLine
      onDraftObjectChange?.(newLine)
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
      lastShapePreviewBoundsRef.current = null
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
      onDraftObjectChange?.(createDraftLine(prev))
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
      schedulePreviewShape(prev)
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
      flushPendingPreviewShape()
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
        color: INFINITY_TEXT_DEFAULT_COLOR,
        fontFamily: INFINITY_TEXT_DEFAULT_FONT_FAMILY,
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
    const targetObject = objectsRef.current.find((object) => object.id === id)
    const isOnlySelectedObject = current.length === 1 && current[0] === id
    if (!isShift && targetObject?.type === 'text' && isOnlySelectedObject) {
      openExistingTextEditor(targetObject)
      return
    }

    let next: string[]
    if (isShift) {
      next = current.includes(id)
        ? current.filter((selectedId) => selectedId !== id)
        : [...current, id]
    } else {
      if (current.length === 1 && current[0] === id) {
        if (targetObject?.type === 'text') {
          openExistingTextEditor(targetObject)
        }
        return
      }
      next = [id]
    }
    recordSelection(next)
  }

  const onObjectDragEnd = (id: string, x: number, y: number) => {
    if (!canEdit(id)) {
      blockEdit(id)
      return
    }
    const draggedObject = objectsRef.current.find((object) => object.id === id)
    if (!draggedObject) return

    const selectedIds = selectedIdsRef.current
    const moveIds = selectedIds.length > 1 && selectedIds.includes(id)
      ? selectedIds
      : [id]
    const blockedId = moveIds.find((elementId) => !canEdit(elementId))
    if (blockedId) {
      blockEdit(blockedId)
      resetLineNodeOffsets(moveIds)
      return
    }

    const delta = getDragDeltaFromObject(draggedObject, x, y)
    resetLineNodeOffsets(moveIds)
    if (delta.x === 0 && delta.y === 0) return

    const moveIdSet = new Set(moveIds)
    const updatedObjects: InfinityObject[] = []
    const newObjects = objectsRef.current.map((obj) => {
      if (!moveIdSet.has(obj.id)) return obj
      const updatedObject = moveObjectByDelta(obj, delta.x, delta.y)
      updatedObjects.push(updatedObject)
      return updatedObject
    })
    if (updatedObjects.length === 0) return

    commitLocalChange(
      newObjects,
      selectedIds.includes(id) ? selectedIds : [id],
      updatedObjects.map((updatedObject) => ({
        operationType: 'UPSERT_ELEMENT',
        elementId: updatedObject.id,
        element: { ...updatedObject },
      })),
    )
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
      if (obj.type === 'rect' || obj.type === 'ellipse' || obj.type === 'image') {
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

  const onObjectsTransformEnd = (updatedObjects: InfinityObject[]) => {
    if (updatedObjects.length === 0) return
    const blockedId = updatedObjects.find((object) => !canEdit(object.id))?.id
    if (blockedId) {
      blockEdit(blockedId)
      return
    }

    const updatedObjectMap = new Map(updatedObjects.map((object) => [object.id, object]))
    const newObjects = objectsRef.current.map((object) =>
      updatedObjectMap.get(object.id) ?? object,
    )
    commitLocalChange(
      newObjects,
      selectedIdsRef.current,
      updatedObjects.map((object) => ({
        operationType: 'UPSERT_ELEMENT',
        elementId: object.id,
        element: { ...object },
      })),
    )
  }

  // 텍스트 객체 더블 클릭 → 편집 모드 진입.
  const onTextDblClick = (id: string) => {
    if (!canEdit(id)) {
      blockEdit(id)
      return
    }
    const target = objectsRef.current.find((obj) => obj.id === id)
    if (!target || target.type !== 'text') return
    openExistingTextEditor(target)
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
    const layerMove = moveSelectedObjectsByLayer(objectsRef.current, ids, direction)
    if (!layerMove) return

    commitLocalChange(
      layerMove.newObjects,
      ids,
      layerMove.updatedObjects.map((object) => ({
        operationType: 'UPSERT_ELEMENT',
        elementId: object.id,
        element: { ...object },
      })),
    )
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
    onObjectsTransformEnd,
    onTextDblClick,
    shiftSelectedZIndex,
  } as const
}
