'use client'

import { useEffect, useRef, useState } from 'react'
import type Konva from 'konva'
import { DEFAULT_DRAWING_STROKE_WIDTH } from '@/shared/constants'
import type { InfiniteCanvasOperationRequest } from '@/shared/types'

import {
  type InfinityObject,
  type InfinityText,
  type InfinityToolKey,
} from '../constants'
import { createClientOperationId } from '../infinityObjectUtils'
import { useInfinityHistory } from './useInfinityHistory'
import { useInfinityViewport } from './useInfinityViewport'
import { useInfinityEvents } from './useInfinityEvents'

interface UseInfinityDrawingNodeRefs {
  currentPenLineRef: React.RefObject<Konva.Line | null>
  currentEraserLineRef: React.RefObject<Konva.Line | null>
  previewRectRef: React.RefObject<Konva.Rect | null>
  previewEllipseRef: React.RefObject<Konva.Ellipse | null>
  cursorPreviewRef: React.RefObject<Konva.Circle | null>
  selectionBoxRef: React.RefObject<Konva.Rect | null>
}

interface UseInfinityDrawingOptions {
  canEditObject?: (id: string) => boolean
  onBlockedObjectEdit?: (id: string) => void
  onDraftObjectChange?: (draftObject: InfinityObject | InfinityObject[] | null) => void
  onLocalOperations?: (operations: InfiniteCanvasOperationRequest[]) => void
}

type LocalOperationDescriptor = Omit<InfiniteCanvasOperationRequest, 'clientOperationId'>

interface LocalEditAction {
  undoObjects: InfinityObject[]
  undoSelectedIds: string[]
  undoOperations: LocalOperationDescriptor[]
  redoObjects: InfinityObject[]
  redoSelectedIds: string[]
  redoOperations: LocalOperationDescriptor[]
}

export interface InfinityTextEditorState {
  x: number
  y: number
  fontSize: number
  color: string
  initialText: string
  /** 기존 텍스트 객체 편집인 경우 id; 신규 생성이면 null. */
  editingId: string | null
}

const CLIPBOARD_PASTE_OFFSET = 28

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
    return true
  }
  return target.isContentEditable
}

function createElementId(prefix = 'copy'): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return `${prefix}-${crypto.randomUUID()}`
  }

  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function cloneObjectWithOffset(
  object: InfinityObject,
  offset: number,
): InfinityObject {
  const id = createElementId(object.type)

  if (object.type === 'line') {
    return {
      ...object,
      id,
      points: object.points.map((point) => ({
        x: point.x + offset,
        y: point.y + offset,
      })),
    }
  }

  if (object.type === 'image') {
    return {
      ...object,
      id,
      x: object.x + offset,
      y: object.y + offset,
      metadata: object.metadata ? { ...object.metadata } : undefined,
    }
  }

  return {
    ...object,
    id,
    x: object.x + offset,
    y: object.y + offset,
  }
}

const objectSignatureCache = new WeakMap<InfinityObject, string>()

function getInfinityObjectSignature(object: InfinityObject) {
  const cachedSignature = objectSignatureCache.get(object)
  if (cachedSignature) return cachedSignature

  const signature = JSON.stringify(object)
  objectSignatureCache.set(object, signature)
  return signature
}

function areInfinityObjectsEqual(firstObject: InfinityObject, secondObject: InfinityObject) {
  if (firstObject === secondObject) return true
  return getInfinityObjectSignature(firstObject) === getInfinityObjectSignature(secondObject)
}

export function useInfinityDrawing(
  stageRef: React.RefObject<Konva.Stage | null>,
  nodeRefs: UseInfinityDrawingNodeRefs,
  options: UseInfinityDrawingOptions = {},
) {
  const history = useInfinityHistory()
  const viewport = useInfinityViewport(stageRef)

  const [tool, setToolState] = useState<InfinityToolKey>('pen')
  const [color, setColor] = useState<string>('#2e73f2')
  const [strokeWidth, setStrokeWidth] = useState<number>(DEFAULT_DRAWING_STROKE_WIDTH)

  const [isShiftDown, setIsShiftDown] = useState<boolean>(false)
  const isShiftDownRef = useRef<boolean>(false)

  const [textEditor, setTextEditor] = useState<InfinityTextEditorState | null>(
    null,
  )
  const localEditHistoryRef = useRef<{ actions: LocalEditAction[]; index: number }>({
    actions: [],
    index: -1,
  })
  const [localHistoryCursor, setLocalHistoryCursor] = useState({ index: -1, length: 0 })
  const internalClipboardRef = useRef<InfinityObject[]>([])
  const pasteCountRef = useRef(0)

  const emitLocalOperations = (operations: LocalOperationDescriptor[]) => {
    if (operations.length === 0) return
    options.onLocalOperations?.(
      operations.map((operation) => ({
        ...operation,
        clientOperationId: createClientOperationId(),
      })),
    )
  }

  const createRestoreOperations = (
    previousObjects: InfinityObject[],
    nextObjects: InfinityObject[],
  ): LocalOperationDescriptor[] => {
    const previousObjectMap = new Map(previousObjects.map((object) => [object.id, object]))
    const nextObjectMap = new Map(nextObjects.map((object) => [object.id, object]))
    const operations: LocalOperationDescriptor[] = []

    for (const previousObject of previousObjects) {
      if (nextObjectMap.has(previousObject.id)) continue
      operations.push({
        operationType: 'DELETE_ELEMENT',
        elementId: previousObject.id,
      })
    }

    for (const nextObject of nextObjects) {
      const previousObject = previousObjectMap.get(nextObject.id)
      if (previousObject && areInfinityObjectsEqual(previousObject, nextObject)) continue
      operations.push({
        operationType: 'UPSERT_ELEMENT',
        elementId: nextObject.id,
        element: { ...nextObject },
      })
    }

    return operations
  }

  const pushLocalEditAction = (action: LocalEditAction) => {
    const { actions, index } = localEditHistoryRef.current
    const nextActions = actions.slice(0, index + 1)
    nextActions.push(action)
    localEditHistoryRef.current = { actions: nextActions, index: nextActions.length - 1 }
    setLocalHistoryCursor({ index: nextActions.length - 1, length: nextActions.length })
  }

  const commitLocalChange = (
    newObjects: InfinityObject[],
    newSelectedIds: string[],
    redoOperations: LocalOperationDescriptor[],
  ) => {
    const undoObjects = [...history.objectsRef.current]
    const undoSelectedIds = [...history.selectedIdsRef.current]
    const undoOperations = createRestoreOperations(newObjects, undoObjects)

    history.saveSnapshot(newObjects, newSelectedIds)
    emitLocalOperations(redoOperations)
    pushLocalEditAction({
      undoObjects,
      undoSelectedIds,
      undoOperations,
      redoObjects: [...newObjects],
      redoSelectedIds: [...newSelectedIds],
      redoOperations,
    })
  }

  const openTextEditor = (request: {
    x: number
    y: number
    fontSize: number
    color: string
    editingId: string | null
  }) => {
    const editingObject = request.editingId
      ? history.objectsRef.current.find((obj) => obj.id === request.editingId)
      : null
    const initialText =
      editingObject && editingObject.type === 'text' ? editingObject.text : ''
    setTextEditor({
      x: request.x,
      y: request.y,
      fontSize: request.fontSize,
      color: request.color,
      initialText,
      editingId: request.editingId,
    })
  }

  const closeTextEditor = () => {
    setTextEditor(null)
  }

  const commitTextEditor = (text: string, fontSize: number) => {
    const editor = textEditor
    if (!editor) return
    const trimmed = text
    if (editor.editingId) {
      // 기존 텍스트 편집 — 빈 문자열이면 삭제.
      if (trimmed.length === 0) {
        const newObjects = history.objectsRef.current.filter(
          (obj) => obj.id !== editor.editingId,
        )
        const newSelected = history.selectedIdsRef.current.filter(
          (id) => id !== editor.editingId,
        )
        commitLocalChange(newObjects, newSelected, [
          {
            operationType: 'DELETE_ELEMENT',
            elementId: editor.editingId,
          },
        ])
      } else {
        const newObjects = history.objectsRef.current.map((obj) => {
          if (obj.id !== editor.editingId) return obj
          if (obj.type === 'text') {
            return { ...obj, text: trimmed, fontSize }
          }
          return obj
        })
        const updatedText = newObjects.find((object) => object.id === editor.editingId)
        if (updatedText) {
          commitLocalChange(newObjects, history.selectedIdsRef.current, [
            {
              operationType: 'UPSERT_ELEMENT',
              elementId: updatedText.id,
              element: { ...updatedText },
            },
          ])
        }
      }
    } else if (trimmed.length > 0) {
      const id = Math.random().toString(36).slice(2, 9)
      const newText: InfinityText = {
        id,
        type: 'text',
        x: editor.x,
        y: editor.y,
        text: trimmed,
        fontSize,
        color: editor.color,
      }
      const newObjects: InfinityObject[] = [
        ...history.objectsRef.current,
        newText,
      ]
      commitLocalChange(newObjects, [id], [
        {
          operationType: 'UPSERT_ELEMENT',
          elementId: newText.id,
          element: { ...newText },
        },
      ])
    }
    setTextEditor(null)
  }

  const events = useInfinityEvents({
    commitLocalChange,
    silentClearSelection: history.silentClearSelection,
    silentSetSelection: history.silentSetSelection,
    recordSelection: history.recordSelection,
    onDraftObjectChange: options.onDraftObjectChange,
    objectsRef: history.objectsRef,
    selectedIdsRef: history.selectedIdsRef,
    color,
    strokeWidth,
    isSpaceDownRef: viewport.isSpaceDownRef,
    isShiftDownRef,
    openTextEditor,
    canEditObject: options.canEditObject,
    onBlockedObjectEdit: options.onBlockedObjectEdit,
    stageRef,
    ...nodeRefs,
  })

  const setTool = (newTool: InfinityToolKey) => {
    setToolState(newTool)
    if (newTool !== 'select') {
      history.silentClearSelection()
    }
  }

  const clearAll = () => {
    commitLocalChange([], [], [{ operationType: 'CLEAR_CANVAS' }])
    events.resetDrawingState()
  }

  const addObject = (object: InfinityObject) => {
    setToolState('select')
    commitLocalChange([...history.objectsRef.current, object], [object.id], [
      {
        operationType: 'UPSERT_ELEMENT',
        elementId: object.id,
        element: { ...object },
      },
    ])
  }

  // Stable refs so the keyboard handler never goes stale.
  const undoRef = useRef(history.undo)
  const redoRef = useRef(history.redo)
  const copySelectedRef = useRef(() => {})
  const pasteSelectedRef = useRef(() => {})
  const cutSelectedRef = useRef(() => {})
  const deleteSelectedRef = useRef(() => {})
  const setSpacePanningRef = useRef(viewport.setSpacePanning)
  const setToolPanningRef = useRef(viewport.setToolPanning)
  const shiftSelectedZIndexRef = useRef(events.shiftSelectedZIndex)

  useEffect(() => {
    const getEditableSelectedObjects = () => {
      const selectedIds = history.selectedIdsRef.current
      if (selectedIds.length === 0) return []

      const selectedIdSet = new Set(selectedIds)
      const blockedId = selectedIds.find((selectedId) => {
        const object = history.objectsRef.current.find((currentObject) => currentObject.id === selectedId)
        return object && options.canEditObject && !options.canEditObject(selectedId)
      })
      if (blockedId) {
        options.onBlockedObjectEdit?.(blockedId)
        return []
      }

      return history.objectsRef.current.filter((object) => selectedIdSet.has(object.id))
    }

    copySelectedRef.current = () => {
      const selectedObjects = getEditableSelectedObjects()
      if (selectedObjects.length === 0) return

      internalClipboardRef.current = selectedObjects.map((object) => ({ ...object }))
      pasteCountRef.current = 0
    }
    deleteSelectedRef.current = () => {
      const selectedObjects = getEditableSelectedObjects()
      if (selectedObjects.length === 0) return

      const selectedIdSet = new Set(selectedObjects.map((object) => object.id))
      const nextObjects = history.objectsRef.current.filter((object) => !selectedIdSet.has(object.id))
      commitLocalChange(
        nextObjects,
        [],
        selectedObjects.map((object) => ({
          operationType: 'DELETE_ELEMENT',
          elementId: object.id,
        })),
      )
    }
    cutSelectedRef.current = () => {
      copySelectedRef.current()
      if (internalClipboardRef.current.length === 0) return
      deleteSelectedRef.current()
    }
    pasteSelectedRef.current = () => {
      const copiedObjects = internalClipboardRef.current
      if (copiedObjects.length === 0) return

      pasteCountRef.current += 1
      const offset = CLIPBOARD_PASTE_OFFSET * pasteCountRef.current
      const pastedObjects = copiedObjects.map((object) => cloneObjectWithOffset(object, offset))
      commitLocalChange(
        [...history.objectsRef.current, ...pastedObjects],
        pastedObjects.map((object) => object.id),
        pastedObjects.map((object) => ({
          operationType: 'UPSERT_ELEMENT',
          elementId: object.id,
          element: { ...object },
        })),
      )
      setToolState('select')
    }
    undoRef.current = () => {
      const { actions, index } = localEditHistoryRef.current
      if (index < 0) return
      const action = actions[index]
      history.saveSnapshot(action.undoObjects, action.undoSelectedIds)
      emitLocalOperations(action.undoOperations)
      const nextIndex = index - 1
      localEditHistoryRef.current = { actions, index: nextIndex }
      setLocalHistoryCursor({ index: nextIndex, length: actions.length })
    }
    redoRef.current = () => {
      const { actions, index } = localEditHistoryRef.current
      if (index >= actions.length - 1) return
      const nextIndex = index + 1
      const action = actions[nextIndex]
      history.saveSnapshot(action.redoObjects, action.redoSelectedIds)
      emitLocalOperations(action.redoOperations)
      localEditHistoryRef.current = { actions, index: nextIndex }
      setLocalHistoryCursor({ index: nextIndex, length: actions.length })
    }
    setSpacePanningRef.current = viewport.setSpacePanning
    setToolPanningRef.current = viewport.setToolPanning
    shiftSelectedZIndexRef.current = events.shiftSelectedZIndex
  })

  const undo = () => undoRef.current()
  const redo = () => redoRef.current()

  useEffect(() => {
    setToolPanningRef.current(tool === 'hand')
    return () => setToolPanningRef.current(false)
  }, [tool])

  // ── Keyboard / Space / Shift / 도구 / z-index 단축키 ────────────────────────
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const typingTarget = isTypingTarget(e.target)

      if (e.code === 'Space' && !viewport.isSpaceDownRef.current && !typingTarget) {
        e.preventDefault()
        viewport.isSpaceDownRef.current = true
        setSpacePanningRef.current(true)
      }
      if (e.key === 'Shift' && !isShiftDownRef.current) {
        isShiftDownRef.current = true
        setIsShiftDown(true)
      }

      if (typingTarget || e.ctrlKey || e.metaKey || e.altKey) {
        // 단축키 라우팅은 typing 중 / 보조키 조합일 때 차단.
        // Ctrl+Z/Y만 별도 분기에서 처리.
      } else if (e.key === 'p') {
        e.preventDefault()
        setToolState('pen')
      } else if (e.key === 'e') {
        e.preventDefault()
        setToolState('eraser')
      } else if (e.key === 's') {
        e.preventDefault()
        setToolState('select-eraser')
      } else if (e.key === 't') {
        e.preventDefault()
        setToolState('text')
      } else if (e.key === 'v') {
        e.preventDefault()
        setToolState('select')
      } else if (e.key === 'h') {
        e.preventDefault()
        setToolState('hand')
      } else if (e.key === '[') {
        e.preventDefault()
        shiftSelectedZIndexRef.current(-1)
      } else if (e.key === ']') {
        e.preventDefault()
        shiftSelectedZIndexRef.current(1)
      }

      const isShortcutKey = e.ctrlKey || e.metaKey
      const normalizedKey = e.key.toLowerCase()

      if (!typingTarget && isShortcutKey && !e.shiftKey && normalizedKey === 'z') {
        e.preventDefault()
        undoRef.current()
      }
      if (
        !typingTarget &&
        ((isShortcutKey && e.shiftKey && normalizedKey === 'z') ||
          (isShortcutKey && normalizedKey === 'y'))
      ) {
        e.preventDefault()
        redoRef.current()
      }
      if (!typingTarget && isShortcutKey && normalizedKey === 'c') {
        e.preventDefault()
        copySelectedRef.current()
      }
      if (!typingTarget && isShortcutKey && normalizedKey === 'v') {
        e.preventDefault()
        pasteSelectedRef.current()
      }
      if (!typingTarget && isShortcutKey && normalizedKey === 'x') {
        e.preventDefault()
        cutSelectedRef.current()
      }
      if (!typingTarget && !isShortcutKey && (e.key === 'Delete' || e.key === 'Backspace')) {
        e.preventDefault()
        deleteSelectedRef.current()
      }
    }

    const onKeyUp = (e: KeyboardEvent) => {
      if (e.code === 'Space') {
        viewport.isSpaceDownRef.current = false
        setSpacePanningRef.current(false)
      }
      if (e.key === 'Shift') {
        isShiftDownRef.current = false
        setIsShiftDown(false)
      }
    }

    window.addEventListener('keydown', onKeyDown)
    window.addEventListener('keyup', onKeyUp)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('keyup', onKeyUp)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return {
    objects: history.objects,
    selectedIds: history.selectedIds,

    tool,
    color,
    strokeWidth,
    setTool,
    setColor,
    setStrokeWidth,

    canUndo: localHistoryCursor.index >= 0,
    canRedo: localHistoryCursor.index < localHistoryCursor.length - 1,
    undo,
    redo,
    clearAll,
    addObject,
    clearSelection: history.silentClearSelection,
    replaceObjectsFromServer: history.replaceObjectsFromServer,
    syncObjectsFromServer: history.syncObjectsFromServer,
    shiftSelectedZIndex: events.shiftSelectedZIndex,

    isShiftDown,

    textEditor,
    closeTextEditor,
    commitTextEditor,

    viewport: {
      scaleRef: viewport.scaleRef,
      stagePosRef: viewport.stagePosRef,
      centerInitialViewport: viewport.centerInitialViewport,
      setPointerPanning: viewport.setPointerPanning,
    },

    handlers: {
      onStageMouseDown: events.onStageMouseDown,
      onStageMouseMove: events.onStageMouseMove,
      onStageMouseUp: events.onStageMouseUp,
      onStageMouseLeave: events.onStageMouseLeave,
      onStageWheel: viewport.onStageWheel,
      onStageDragEnd: viewport.onStageDragEnd,
      onStageClick: events.onStageClick,
      onObjectClick: events.onObjectClick,
      onObjectDragEnd: events.onObjectDragEnd,
      onShapeTransformEnd: events.onShapeTransformEnd,
      onTextTransformEnd: events.onTextTransformEnd,
      onObjectsTransformEnd: events.onObjectsTransformEnd,
      onTextDblClick: events.onTextDblClick,
    },
  } as const
}
