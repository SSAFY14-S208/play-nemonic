'use client'

import { useRef } from 'react'
import type Konva from 'konva'

import {
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

const MIN_LINE_POINT_DISTANCE = 2.5
const MAX_LINE_POINTS_PER_OBJECT = 800
const MAX_DRAFT_LINE_POINTS = 120

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

interface UseInfinityEventsParams {
  saveSnapshot: (newObjects: InfinityObject[], selectedIds: string[]) => void
  silentClearSelection: () => void
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
  saveSnapshot,
  silentClearSelection,
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
    hideCurrentLines()
    hidePreviewShapes()
    hideCursor()
    hideSelectionBox()
    applyHoverOpacity(new Set())
    isDrawingRef.current = false
    onDraftObjectChange?.(null)
  }

  // ── AABB 교차 헬퍼 — 드래그 박스 vs 객체 ──────────────────────────────────
  const intersects = (
    a: { x: number; y: number; width: number; height: number },
    b: { x: number; y: number; width: number; height: number },
  ): boolean => {
    return (
      a.x < b.x + b.width &&
      a.x + a.width > b.x &&
      a.y < b.y + b.height &&
      a.y + a.height > b.y
    )
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

    // 커서 미리보기 위치 갱신은 isDrawing 무관하게 항상.
    updateCursorForTool(pos, toolSnapshot)

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
      showSelectionBox({ x, y, width, height })
    }
  }

  const onStageMouseUp = (toolSnapshot: InfinityToolKey) => {
    if (!isDrawingRef.current) return
    isDrawingRef.current = false

    if (toolSnapshot === 'pen' || toolSnapshot === 'eraser') {
      const line = currentLineRef.current
      if (line && line.points.length > 1) {
        saveSnapshot([...objectsRef.current, createPersistedLine(line)], selectedIdsRef.current)
      }
      currentLineRef.current = null
      onDraftObjectChange?.(null)
      hideCurrentLines()
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
        saveSnapshot(newObjects, newSelected)
      }
      applyHoverOpacity(new Set())
    } else if (isShapeTool(toolSnapshot)) {
      const shape = previewShapeRef.current
      if (shape && (Math.abs(shape.width) > 5 || Math.abs(shape.height) > 5)) {
        saveSnapshot([...objectsRef.current, shape], [shape.id])
      }
      previewShapeRef.current = null
      onDraftObjectChange?.(null)
      hidePreviewShapes()
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
            const baseSet = new Set(dragStart.baseSelection)
            const hitIds: string[] = [...dragStart.baseSelection]
            for (const obj of objectsRef.current) {
              if (!canEdit(obj.id)) continue
              const objNode = stage.findOne(`#${obj.id}`)
              if (!objNode) continue
              const rect = objNode.getClientRect({ relativeTo: stage })
              if (intersects(box, rect) && !baseSet.has(obj.id)) {
                hitIds.push(obj.id)
              }
            }
            saveSnapshot(objectsRef.current, hitIds)
          }
        }
        hideSelectionBox()
      }
      dragSelectStartRef.current = null
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
  const onObjectClick = (id: string, isShift: boolean) => {
    if (!canEdit(id)) {
      blockEdit(id)
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
    saveSnapshot(newObjects, selectedIdsRef.current)
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
    saveSnapshot(newObjects, selectedIdsRef.current)
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
    saveSnapshot(newObjects, selectedIdsRef.current)
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
    if (changed) saveSnapshot(objects, ids)
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
