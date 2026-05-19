'use client'

import { Fragment, memo, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactElement, RefObject } from 'react'
import { Stage, Layer, Rect, Ellipse, Line, Transformer, Label, Tag, Text, Circle, Path, Group } from 'react-konva'
import Konva from 'konva'

import { INFINITY_LINE_TENSION } from '../constants'
import type { InfinityImage, InfinityLine, InfinityObject, InfinityShape, InfinityText as InfinityTextObject, InfinityToolKey } from '../constants'
import type { useInfinityDrawing } from '../hooks'
import {
  CursorPreview,
  DotGridShape,
  KonvaFill,
  KonvaEllipse,
  KonvaImageObject,
  KonvaLine,
  KonvaRect,
  KonvaText,
  SelectionBox,
} from './stage'

type DrawingState = ReturnType<typeof useInfinityDrawing>

export interface InfinityLockedElementView {
  elementId: string
  userUuid: string
  nickname: string
  color: string
  identityIndex: number
}

export interface InfinityRemoteCursorView {
  userUuid: string
  nickname: string
  color: string
  x: number
  y: number
  identityIndex: number
}

export interface InfinityRemoteDraftObjectView {
  userUuid: string
  nickname: string
  color: string
  identityIndex: number
  object: InfinityObject
}

interface SmoothRemoteCursorView extends InfinityRemoteCursorView {
  targetX: number
  targetY: number
}

interface InfinityCanvasStageProps {
  width: number
  height: number
  stageRef: React.RefObject<Konva.Stage | null>
  drawing: DrawingState
  currentPenLineRef: React.RefObject<Konva.Line | null>
  currentEraserLineRef: React.RefObject<Konva.Line | null>
  previewRectRef: React.RefObject<Konva.Rect | null>
  previewEllipseRef: React.RefObject<Konva.Ellipse | null>
  cursorPreviewRef: React.RefObject<Konva.Circle | null>
  selectionBoxRef: React.RefObject<Konva.Rect | null>
  isShiftDown: boolean
  lockedElements: InfinityLockedElementView[]
  lockedElementIds: Set<string>
  remoteDraftObjects: InfinityRemoteDraftObjectView[]
  remoteCursors: InfinityRemoteCursorView[]
  onCursorMove: (cursor: { x: number; y: number; zoom: number }) => void
  onLayerMenuRequest: (request: { elementId: string; x: number; y: number }) => void
  onSelectionInteractionEnd: (elementIds: string[]) => void
  onDraftObjectsChange: (draftObjects: InfinityObject[] | null) => void
}

interface PartitionedInfinityObjects {
  fills: InfinityObject[]
  lines: InfinityLine[]
  shapeAndTextObjects: InfinityObject[]
}

const REMOTE_CURSOR_SMOOTHING = 0.28
const REMOTE_CURSOR_SETTLE_DISTANCE = 0.35
const REMOTE_CURSOR_PATH = 'M0 0 L0 22 L6 16 L10 26 L14 24 L10 15 L19 15 Z'
const REMOTE_CURSOR_LABEL_OFFSET = { x: 18, y: 24 } as const
const LOCK_LABEL_OFFSET = { x: -8, y: -34 } as const
export const INFINITY_CANVAS_BACKGROUND_LAYER_ID = 'infinity-canvas-background-layer'
const ImperativeEllipse = Ellipse as unknown as (props: {
  ref: RefObject<Konva.Ellipse | null>
  stroke: string
  strokeWidth: number
  fill: string
  dash: number[]
}) => ReactElement

function createRemoteDraftNode(draft: InfinityRemoteDraftObjectView) {
  const object = draft.object

  if (object.type === "line") {
    return new Konva.Line({
      points: object.points.flatMap((point) => [point.x, point.y]),
      stroke: object.color,
      strokeWidth: object.strokeWidth,
      lineCap: "round",
      lineJoin: "round",
      tension: INFINITY_LINE_TENSION,
      perfectDrawEnabled: false,
      listening: false,
      globalCompositeOperation: "source-over",
    });
  }

  if (object.type === "rect") {
    const isFilled = Boolean(object.fill);
    return new Konva.Rect({
      x: object.x,
      y: object.y,
      width: object.width,
      height: object.height,
      rotation: object.rotation ?? 0,
      stroke: isFilled ? undefined : object.color,
      strokeWidth: isFilled ? 0 : object.strokeWidth,
      fill: object.fill ?? "transparent",
      listening: false,
    });
  }

  if (object.type === "ellipse") {
    const isFilled = Boolean(object.fill);
    return new Konva.Ellipse({
      x: object.x + object.width / 2,
      y: object.y + object.height / 2,
      radiusX: Math.abs(object.width / 2),
      radiusY: Math.abs(object.height / 2),
      rotation: object.rotation ?? 0,
      stroke: isFilled ? undefined : object.color,
      strokeWidth: isFilled ? 0 : object.strokeWidth,
      fill: object.fill ?? "transparent",
      listening: false,
    });
  }

  return null;
}

function updateRemoteDraftNode(node: Konva.Node, draft: InfinityRemoteDraftObjectView) {
  const object = draft.object

  if (object.type === "line" && node instanceof Konva.Line) {
    node.setAttrs({
      points: object.points.flatMap((point) => [point.x, point.y]),
      stroke: object.color,
      strokeWidth: object.strokeWidth,
    });
    return;
  }

  if (object.type === "rect" && node instanceof Konva.Rect) {
    const isFilled = Boolean(object.fill);
    node.setAttrs({
      x: object.x,
      y: object.y,
      width: object.width,
      height: object.height,
      rotation: object.rotation ?? 0,
      stroke: isFilled ? undefined : object.color,
      strokeWidth: isFilled ? 0 : object.strokeWidth,
      fill: object.fill ?? "transparent",
    });
    return;
  }

  if (object.type === "ellipse" && node instanceof Konva.Ellipse) {
    const isFilled = Boolean(object.fill);
    node.setAttrs({
      x: object.x + object.width / 2,
      y: object.y + object.height / 2,
      radiusX: Math.abs(object.width / 2),
      radiusY: Math.abs(object.height / 2),
      rotation: object.rotation ?? 0,
      stroke: isFilled ? undefined : object.color,
      strokeWidth: isFilled ? 0 : object.strokeWidth,
      fill: object.fill ?? "transparent",
    });
  }
}

const ImperativeRemoteDraftLayer = memo(function ImperativeRemoteDraftLayer({
  remoteDraftObjects,
}: {
  remoteDraftObjects: InfinityRemoteDraftObjectView[]
}) {
  const layerRef = useRef<Konva.Layer>(null)
  const draftObjectsRef = useRef(remoteDraftObjects)
  const nodeMapRef = useRef<Map<string, Konva.Node>>(new Map())
  const animationFrameRef = useRef<number | null>(null)

  const flushDraftNodes = useCallback(() => {
    animationFrameRef.current = null
    const layer = layerRef.current
    if (!layer) return

    const nextKeys = new Set<string>()
    for (const draft of draftObjectsRef.current) {
      const object = draft.object
      if (object.type === "line" && object.isEraser) continue
      const key = `${draft.userUuid}:${object.id}`
      nextKeys.add(key)

      const existingNode = nodeMapRef.current.get(key)
      if (existingNode) {
        updateRemoteDraftNode(existingNode, draft)
        continue
      }

      const nextNode = createRemoteDraftNode(draft)
      if (!nextNode) continue
      nodeMapRef.current.set(key, nextNode)
      layer.add(nextNode)
    }

    for (const [key, node] of nodeMapRef.current) {
      if (nextKeys.has(key)) continue
      node.destroy()
      nodeMapRef.current.delete(key)
    }

    layer.batchDraw()
  }, [])

  useEffect(() => {
    draftObjectsRef.current = remoteDraftObjects
    if (animationFrameRef.current !== null) return
    animationFrameRef.current = window.requestAnimationFrame(flushDraftNodes)
  }, [flushDraftNodes, remoteDraftObjects])

  useEffect(
    () => () => {
      if (animationFrameRef.current !== null) {
        window.cancelAnimationFrame(animationFrameRef.current)
      }
      for (const node of nodeMapRef.current.values()) {
        node.destroy()
      }
      nodeMapRef.current.clear()
    },
    [],
  )

  return <Layer ref={layerRef} listening={false} />
})

function getReadableTextColor(backgroundColor: string) {
  const match = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(backgroundColor)
  if (!match) return '#2d3a55'

  const red = Number.parseInt(match[1] ?? '00', 16) / 255
  const green = Number.parseInt(match[2] ?? '00', 16) / 255
  const blue = Number.parseInt(match[3] ?? '00', 16) / 255
  const luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
  return luminance > 0.62 ? '#2d3a55' : '#ffffff'
}

function useSmoothRemoteCursors(remoteCursors: InfinityRemoteCursorView[]) {
  const targetCursorsRef = useRef(remoteCursors)
  const animationFrameRef = useRef<number | null>(null)
  const scheduleTickRef = useRef<() => void>(() => undefined)
  const [smoothCursors, setSmoothCursors] = useState<SmoothRemoteCursorView[]>(() =>
    remoteCursors.map((cursor) => ({
      ...cursor,
      targetX: cursor.x,
      targetY: cursor.y,
    })),
  )

  const tick = useCallback(() => {
    animationFrameRef.current = null
    let shouldKeepAnimating = false

    setSmoothCursors((currentCursors) => {
      const targetCursors = targetCursorsRef.current
      if (currentCursors.length === 0 && targetCursors.length === 0) {
        return currentCursors
      }

      const currentCursorMap = new Map(currentCursors.map((cursor) => [cursor.userUuid, cursor]))
      let changed = currentCursors.length !== targetCursors.length
      const nextCursors = targetCursors.map((targetCursor) => {
        const currentCursor = currentCursorMap.get(targetCursor.userUuid)
        if (!currentCursor) {
          changed = true
          shouldKeepAnimating = true
          return {
            ...targetCursor,
            targetX: targetCursor.x,
            targetY: targetCursor.y,
          }
        }

        const deltaX = targetCursor.x - currentCursor.x
        const deltaY = targetCursor.y - currentCursor.y
        const isSettled =
          Math.abs(deltaX) < REMOTE_CURSOR_SETTLE_DISTANCE &&
          Math.abs(deltaY) < REMOTE_CURSOR_SETTLE_DISTANCE
        const nextX = isSettled ? targetCursor.x : currentCursor.x + deltaX * REMOTE_CURSOR_SMOOTHING
        const nextY = isSettled ? targetCursor.y : currentCursor.y + deltaY * REMOTE_CURSOR_SMOOTHING

        if (!isSettled) {
          shouldKeepAnimating = true
        }

        if (
          nextX !== currentCursor.x ||
          nextY !== currentCursor.y ||
          targetCursor.nickname !== currentCursor.nickname ||
          targetCursor.color !== currentCursor.color ||
          targetCursor.identityIndex !== currentCursor.identityIndex
        ) {
          changed = true
        }

        return {
          ...targetCursor,
          x: nextX,
          y: nextY,
          targetX: targetCursor.x,
          targetY: targetCursor.y,
        }
      })

      return changed ? nextCursors : currentCursors
    })

    if (shouldKeepAnimating) {
      scheduleTickRef.current()
    }
  }, [])

  const scheduleTick = useCallback(() => {
    if (animationFrameRef.current !== null) return
    animationFrameRef.current = window.requestAnimationFrame(tick)
  }, [tick])

  useEffect(() => {
    scheduleTickRef.current = scheduleTick
  }, [scheduleTick])

  useEffect(() => {
    targetCursorsRef.current = remoteCursors
    if (remoteCursors.length > 0 || smoothCursors.length > 0) {
      scheduleTick()
    }
  }, [remoteCursors, scheduleTick, smoothCursors.length])

  useEffect(
    () => () => {
      if (animationFrameRef.current !== null) {
        window.cancelAnimationFrame(animationFrameRef.current)
      }
    },
    [],
  )

  return smoothCursors
}

function getCursorStyle(tool: InfinityToolKey): string {
  if (tool === "bucket") return "crosshair";
  if (
    tool === "shape-rect" ||
    tool === "shape-ellipse" ||
    tool === "shape-rect-fill" ||
    tool === "shape-ellipse-fill"
  ) {
    return "crosshair";
  }
  if (tool === "text") return "text";
  if (tool === "select") return "default";
  if (tool === "hand") return "grab";
  // pen / eraser / select-eraser는 Konva CursorPreview로 대체 → CSS 커서 숨김.
  return "none";
}

function getLineBounds(line: InfinityLine) {
  if (line.points.length === 0) return null;
  const xValues = line.points.map((point) => point.x);
  const yValues = line.points.map((point) => point.y);
  const minX = Math.min(...xValues);
  const maxX = Math.max(...xValues);
  const minY = Math.min(...yValues);
  const maxY = Math.max(...yValues);
  const padding = Math.max(line.strokeWidth, 8);

  return {
    x: minX - padding,
    y: minY - padding,
    width: Math.max(maxX - minX + padding * 2, padding * 2),
    height: Math.max(maxY - minY + padding * 2, padding * 2),
  };
}

function getObjectBounds(object: InfinityObject) {
  if (object.type === "line") return getLineBounds(object);
  if (object.type === "text") {
    return {
      x: object.x,
      y: object.y,
      width: Math.max(object.text.length * object.fontSize * 0.58, object.fontSize),
      height: object.fontSize * 1.35,
    };
  }

  return {
    x: object.x,
    y: object.y,
    width: Math.abs(object.width),
    height: Math.abs(object.height),
  };
}

function resetNodeScale(node: Konva.Node) {
  if (node.scaleX() === 1 && node.scaleY() === 1) return;
  node.scale({ x: 1, y: 1 });
}

function resetNodeTransform(node: Konva.Node) {
  node.position({ x: 0, y: 0 });
  node.rotation(0);
  resetNodeScale(node);
}

function getNodeScaleMagnitude(node: Konva.Node) {
  return Math.max(Math.abs(node.scaleX()), Math.abs(node.scaleY()));
}

function getDragDeltaFromObject(object: InfinityObject, x: number, y: number) {
  if (object.type === "line") {
    return { x, y };
  }

  return {
    x: x - object.x,
    y: y - object.y,
  };
}

function moveNodeByObjectDelta(object: InfinityObject, node: Konva.Node, deltaX: number, deltaY: number) {
  if (object.type === "line") {
    node.position({ x: deltaX, y: deltaY });
    return;
  }

  if (object.type === "ellipse") {
    node.position({
      x: object.x + object.width / 2 + deltaX,
      y: object.y + object.height / 2 + deltaY,
    });
    return;
  }

  node.position({
    x: object.x + deltaX,
    y: object.y + deltaY,
  });
}

function getOverlayScale(scale: number) {
  return scale > 0 ? 1 / scale : 1;
}

const RemoteCursorLayer = memo(function RemoteCursorLayer({
  remoteCursors,
  scale,
}: {
  remoteCursors: InfinityRemoteCursorView[]
  scale: number
}) {
  const smoothRemoteCursors = useSmoothRemoteCursors(remoteCursors);
  const overlayScale = getOverlayScale(scale);

  const renderRemoteCursor = (cursor: SmoothRemoteCursorView) => {
    return (
      <Group
        key={cursor.userUuid}
        x={cursor.x}
        y={cursor.y}
        scaleX={overlayScale}
        scaleY={overlayScale}
        listening={false}
      >
        <Path
          data={REMOTE_CURSOR_PATH}
          fill={cursor.color}
          shadowColor="rgba(45,58,85,0.2)"
          shadowBlur={8}
          shadowOffset={{ x: 0, y: 3 }}
          listening={false}
        />
        <Circle
          radius={4}
          fill="#ffffff"
          listening={false}
        />
        <Label
          x={REMOTE_CURSOR_LABEL_OFFSET.x}
          y={REMOTE_CURSOR_LABEL_OFFSET.y}
          listening={false}
        >
          <Tag
            fill={cursor.color}
            cornerRadius={10}
            shadowColor="rgba(45,58,85,0.22)"
            shadowBlur={8}
            shadowOffset={{ x: 0, y: 3 }}
          />
          <Text
            text={cursor.nickname}
            fill={getReadableTextColor(cursor.color)}
            fontSize={12}
            fontStyle="bold"
            padding={8}
          />
        </Label>
      </Group>
    );
  };

  return <>{smoothRemoteCursors.map(renderRemoteCursor)}</>;
})

export function InfinityCanvasStage({
  width,
  height,
  stageRef,
  drawing,
  currentPenLineRef,
  currentEraserLineRef,
  previewRectRef,
  previewEllipseRef,
  cursorPreviewRef,
  selectionBoxRef,
  isShiftDown,
  lockedElements,
  lockedElementIds,
  remoteDraftObjects,
  remoteCursors,
  onCursorMove,
  onLayerMenuRequest,
  onSelectionInteractionEnd,
  onDraftObjectsChange,
}: InfinityCanvasStageProps) {
  const {
    objects,
    selectedIds,
    tool,
    textEditor,
    viewport: { scale, scaleRef, stagePosRef, setPointerPanning },
    handlers: {
      onStageMouseDown,
      onStageMouseMove,
      onStageMouseUp,
      onStageMouseLeave,
      onStageWheel,
      onStageDragEnd,
      onStageClick,
      onObjectClick,
      onObjectDragEnd,
      onShapeTransformEnd,
      onTextTransformEnd,
      onObjectsTransformEnd,
      onTextDblClick,
    },
  } = drawing;

  const toolRef = useRef<InfinityToolKey>(tool);
  useEffect(() => {
    toolRef.current = tool;
  }, [tool]);

  const transformerRef = useRef<Konva.Transformer>(null);
  const layerMenuLongPressTimerRef = useRef<number | null>(null);
  const isWheelButtonPanningRef = useRef(false);

  const clearLayerMenuLongPress = useCallback(() => {
    if (layerMenuLongPressTimerRef.current === null) return;
    window.clearTimeout(layerMenuLongPressTimerRef.current);
    layerMenuLongPressTimerRef.current = null;
  }, []);

  useEffect(() => clearLayerMenuLongPress, [clearLayerMenuLongPress]);

  const stopWheelButtonPanning = useCallback(() => {
    if (!isWheelButtonPanningRef.current) return;
    isWheelButtonPanningRef.current = false;
    setPointerPanning(false);
    const stage = stageRef.current;
    if (stage) {
      stage.container().style.cursor = getCursorStyle(toolRef.current);
    }
  }, [setPointerPanning, stageRef]);

  useEffect(() => {
    window.addEventListener("mouseup", stopWheelButtonPanning);
    window.addEventListener("blur", stopWheelButtonPanning);
    return () => {
      window.removeEventListener("mouseup", stopWheelButtonPanning);
      window.removeEventListener("blur", stopWheelButtonPanning);
    };
  }, [stopWheelButtonPanning]);

  // 다중 선택 Transformer: stage.findOne으로 nodes 배열 매핑.
  useEffect(() => {
    const transformer = transformerRef.current;
    if (!transformer) return;
    const stage = transformer.getStage();
    if (tool !== "select" || !stage || selectedIds.length === 0) {
      transformer.nodes([]);
      transformer.getLayer()?.batchDraw();
      return;
    }
    const nodes = selectedIds
      .map((id) => stage.findOne(`#${id}`))
      .filter((n): n is Konva.Node => Boolean(n));
    transformer.nodes(nodes);
    transformer.getLayer()?.batchDraw();
  }, [selectedIds, tool, objects]);

  const isSelectTool = tool === "select";
  const editingId = textEditor?.editingId ?? null;
  const handleObjectClick = useCallback((id: string, isShift: boolean) => {
    onObjectClick(id, isShift, toolRef.current);
  }, [onObjectClick]);

  const requestLayerMenu = useCallback(
    (elementId: string, x: number, y: number) => {
      if (!selectedIds.includes(elementId)) {
        onObjectClick(elementId, false, "select");
      }
      onLayerMenuRequest({ elementId, x, y });
    },
    [onLayerMenuRequest, onObjectClick, selectedIds],
  );

  const objectById = useMemo(
    () => new Map(objects.map((object) => [object.id, object])),
    [objects],
  );

  const previewGroupedObjectMove = useCallback((id: string, x: number, y: number) => {
    if (selectedIds.length <= 1 || !selectedIds.includes(id)) return;

    const draggedObject = objectById.get(id);
    const stage = stageRef.current;
    if (!draggedObject || !stage) return;

    const delta = getDragDeltaFromObject(draggedObject, x, y);
    for (const selectedId of selectedIds) {
      if (selectedId === id) continue;

      const object = objectById.get(selectedId);
      const node = stage.findOne(`#${selectedId}`);
      if (!object || !node) continue;
      moveNodeByObjectDelta(object, node, delta.x, delta.y);
    }

    stage.batchDraw();
  }, [objectById, selectedIds, stageRef]);

  const partitionedObjects = useMemo<PartitionedInfinityObjects>(() => {
    const fills: InfinityObject[] = [];
    const lines: InfinityLine[] = [];
    const shapeAndTextObjects: InfinityObject[] = [];

    for (const object of objects) {
      if (object.type === "fill") {
        fills.push(object);
      } else if (object.type === "line") {
        lines.push(object);
      } else {
        shapeAndTextObjects.push(object);
      }
    }

    return { fills, lines, shapeAndTextObjects };
  }, [objects]);

  const readObjectFromNode = useCallback(
    (
      object: InfinityObject,
      node: Konva.Node,
      { normalizeNode = false }: { normalizeNode?: boolean } = {},
    ): InfinityObject | null => {
      if (object.type === "line" && node instanceof Konva.Line) {
        const transform = node.getTransform().copy();
        const points = object.points.map((point) => transform.point(point));
        const strokeScale = getNodeScaleMagnitude(node);
        if (normalizeNode) resetNodeTransform(node);
        return {
          ...object,
          points,
          strokeWidth: Math.max(1, object.strokeWidth * strokeScale),
        };
      }

      if (object.type === "fill" && node instanceof Konva.Image) {
        const scaleX = node.scaleX();
        const scaleY = node.scaleY();
        if (normalizeNode) resetNodeScale(node);
        return {
          ...object,
          x: node.x(),
          y: node.y(),
          width: object.width * scaleX,
          height: object.height * scaleY,
        };
      }

      if (object.type === "image" && node instanceof Konva.Image) {
        const scaleX = node.scaleX();
        const scaleY = node.scaleY();
        if (normalizeNode) resetNodeScale(node);
        return {
          ...object,
          x: node.x(),
          y: node.y(),
          width: object.width * scaleX,
          height: object.height * scaleY,
          rotation: node.rotation(),
        };
      }

      if (object.type === "rect" && node instanceof Konva.Rect) {
        const scaleX = node.scaleX();
        const scaleY = node.scaleY();
        const scaleMagnitude = getNodeScaleMagnitude(node);
        if (normalizeNode) resetNodeScale(node);
        return {
          ...object,
          x: node.x(),
          y: node.y(),
          width: object.width * scaleX,
          height: object.height * scaleY,
          strokeWidth: Math.max(1, object.strokeWidth * scaleMagnitude),
          rotation: node.rotation(),
        };
      }

      if (object.type === "ellipse" && node instanceof Konva.Ellipse) {
        const radiusX = node.radiusX() * node.scaleX();
        const radiusY = node.radiusY() * node.scaleY();
        const scaleMagnitude = getNodeScaleMagnitude(node);
        if (normalizeNode) resetNodeScale(node);
        return {
          ...object,
          x: node.x() - radiusX,
          y: node.y() - radiusY,
          width: radiusX * 2,
          height: radiusY * 2,
          strokeWidth: Math.max(1, object.strokeWidth * scaleMagnitude),
          rotation: node.rotation(),
        };
      }

      if (object.type === "text" && node instanceof Konva.Text) {
        const fontScale = getNodeScaleMagnitude(node);
        if (normalizeNode) resetNodeScale(node);
        return {
          ...object,
          x: node.x(),
          y: node.y(),
          fontSize: Math.max(8, object.fontSize * fontScale),
          rotation: node.rotation(),
        };
      }

      return null;
    },
    [],
  );

  const readMovedObjectFromNode = useCallback(
    (
      object: InfinityObject,
      node: Konva.Node,
      { normalizeNode = false }: { normalizeNode?: boolean } = {},
    ): InfinityObject | null => {
      if (object.type === "line" && node instanceof Konva.Line) {
        const movedPoints = object.points.map((point) => ({
          x: point.x + node.x(),
          y: point.y + node.y(),
        }));
        if (normalizeNode) {
          node.position({ x: 0, y: 0 });
          resetNodeScale(node);
        }
        return {
          ...object,
          points: movedPoints,
        };
      }

      if (object.type === "fill" && node instanceof Konva.Image) {
        if (normalizeNode) resetNodeScale(node);
        return {
          ...object,
          x: node.x(),
          y: node.y(),
        };
      }

      if (object.type === "image" && node instanceof Konva.Image) {
        if (normalizeNode) resetNodeScale(node);
        return {
          ...object,
          x: node.x(),
          y: node.y(),
          rotation: node.rotation(),
        };
      }

      if (object.type === "rect" && node instanceof Konva.Rect) {
        if (normalizeNode) resetNodeScale(node);
        return {
          ...object,
          x: node.x(),
          y: node.y(),
          rotation: node.rotation(),
        };
      }

      if (object.type === "ellipse" && node instanceof Konva.Ellipse) {
        if (normalizeNode) resetNodeScale(node);
        return {
          ...object,
          x: node.x() - Math.abs(object.width) / 2,
          y: node.y() - Math.abs(object.height) / 2,
          rotation: node.rotation(),
        };
      }

      if (object.type === "text" && node instanceof Konva.Text) {
        if (normalizeNode) resetNodeScale(node);
        return {
          ...object,
          x: node.x(),
          y: node.y(),
          rotation: node.rotation(),
        };
      }

      return null;
    },
    [],
  );

  const commitSelectedNodeTransforms = useCallback(() => {
    const stage = stageRef.current;
    if (!stage || selectedIds.length === 0) return;

    const updatedObjects = selectedIds
      .map((selectedId) => {
        const object = objectById.get(selectedId);
        const node = stage.findOne(`#${selectedId}`);
        if (!object || !node) return null;
        return readObjectFromNode(object, node, { normalizeNode: true });
      })
      .filter((object): object is InfinityObject => Boolean(object));

    if (updatedObjects.length > 0) {
      onObjectsTransformEnd(updatedObjects);
    }
    onSelectionInteractionEnd(selectedIds);
    onDraftObjectsChange(null);
    if (selectedIds.length > 0) {
      transformerRef.current?.nodes([]);
    }
  }, [
    objectById,
    onDraftObjectsChange,
    onObjectsTransformEnd,
    onSelectionInteractionEnd,
    readObjectFromNode,
    selectedIds,
    stageRef,
  ]);

  const commitSelectedNodeMoves = useCallback(() => {
    const stage = stageRef.current;
    if (!stage || selectedIds.length === 0) return;

    const updatedObjects = selectedIds
      .map((selectedId) => {
        const object = objectById.get(selectedId);
        const node = stage.findOne(`#${selectedId}`);
        if (!object || !node) return null;
        return readMovedObjectFromNode(object, node, { normalizeNode: true });
      })
      .filter((object): object is InfinityObject => Boolean(object));

    if (updatedObjects.length > 0) {
      onObjectsTransformEnd(updatedObjects);
    }
    onSelectionInteractionEnd(selectedIds);
    onDraftObjectsChange(null);
  }, [
    objectById,
    onDraftObjectsChange,
    onObjectsTransformEnd,
    onSelectionInteractionEnd,
    readMovedObjectFromNode,
    selectedIds,
    stageRef,
  ]);

  const previewSelectedNodeTransforms = useCallback(() => {
    const stage = stageRef.current;
    if (!stage || selectedIds.length === 0) return;

    const updatedObjects = selectedIds
      .map((selectedId) => {
        const object = objectById.get(selectedId);
        const node = stage.findOne(`#${selectedId}`);
        if (!object || !node) return null;
        return readObjectFromNode(object, node);
      })
      .filter((object): object is InfinityObject => Boolean(object));

    onDraftObjectsChange(updatedObjects.length > 0 ? updatedObjects : null);
  }, [
    objectById,
    onDraftObjectsChange,
    readObjectFromNode,
    selectedIds,
    stageRef,
  ]);

  const previewSelectedNodeMoves = useCallback(() => {
    const stage = stageRef.current;
    if (!stage || selectedIds.length === 0) return;

    const updatedObjects = selectedIds
      .map((selectedId) => {
        const object = objectById.get(selectedId);
        const node = stage.findOne(`#${selectedId}`);
        if (!object || !node) return null;
        return readMovedObjectFromNode(object, node);
      })
      .filter((object): object is InfinityObject => Boolean(object));

    onDraftObjectsChange(updatedObjects.length > 0 ? updatedObjects : null);
  }, [
    objectById,
    onDraftObjectsChange,
    readMovedObjectFromNode,
    selectedIds,
    stageRef,
  ]);

  const renderShapeOrText = (obj: InfinityObject) => {
    const isLocked = lockedElementIds.has(obj.id);
    if (obj.type === "rect") {
      const rectObject = obj as InfinityShape;
      return (
        <KonvaRect
          key={rectObject.id}
          shape={rectObject}
          isSelectTool={isSelectTool}
          isLocked={isLocked}
          isGroupedSelection={selectedIds.length > 1 && selectedIds.includes(rectObject.id)}
          onShapeClick={handleObjectClick}
          onShapeDragMove={previewGroupedObjectMove}
          onShapeDragEnd={onObjectDragEnd}
          onShapeTransformEnd={onShapeTransformEnd}
        />
      );
    }
    if (obj.type === "ellipse") {
      const ellipseObject = obj as InfinityShape;
      return (
        <KonvaEllipse
          key={ellipseObject.id}
          shape={ellipseObject}
          isSelectTool={isSelectTool}
          isLocked={isLocked}
          isGroupedSelection={selectedIds.length > 1 && selectedIds.includes(ellipseObject.id)}
          onShapeClick={handleObjectClick}
          onShapeDragMove={previewGroupedObjectMove}
          onShapeDragEnd={onObjectDragEnd}
          onShapeTransformEnd={onShapeTransformEnd}
        />
      );
    }
    if (obj.type === "text") {
      const textObject = obj as InfinityTextObject;
      return (
        <KonvaText
          key={textObject.id}
          textObject={textObject}
          isSelectTool={isSelectTool}
          isEditing={editingId === textObject.id}
          isLocked={isLocked}
          isGroupedSelection={selectedIds.length > 1 && selectedIds.includes(textObject.id)}
          onTextClick={handleObjectClick}
          onTextDblClick={onTextDblClick}
          onTextDragMove={previewGroupedObjectMove}
          onTextDragEnd={onObjectDragEnd}
          onTextTransformEnd={onTextTransformEnd}
        />
      );
    }
    if (obj.type === "image") {
      const imageObject = obj as InfinityImage;
      return (
        <KonvaImageObject
          key={imageObject.id}
          imageObject={imageObject}
          isSelectTool={isSelectTool}
          isLocked={isLocked}
          isGroupedSelection={selectedIds.length > 1 && selectedIds.includes(imageObject.id)}
          onImageClick={handleObjectClick}
          onImageDragMove={previewGroupedObjectMove}
          onImageDragEnd={onObjectDragEnd}
          onImageTransformEnd={onShapeTransformEnd}
        />
      );
    }
    return null;
  };

  const renderLine = useCallback((obj: InfinityObject) => {
    if (obj.type !== "line") return null;
    const isLocked = lockedElementIds.has(obj.id);
    return (
      <KonvaLine
        key={obj.id}
        line={obj}
        isSelectTool={isSelectTool}
        isLocked={isLocked}
        onLineClick={handleObjectClick}
        onLineDragMove={previewGroupedObjectMove}
        onLineDragEnd={onObjectDragEnd}
      />
    );
  }, [handleObjectClick, isSelectTool, lockedElementIds, onObjectDragEnd, previewGroupedObjectMove]);

  const renderRemoteDraftObject = (draft: InfinityRemoteDraftObjectView) => {
    const obj = draft.object

    if (obj.type === "line") {
      const points = obj.points.flatMap((point) => [point.x, point.y])
      return (
        <Line
          key={`remote-draft-${draft.userUuid}-${obj.id}`}
          points={points}
          stroke={obj.isEraser ? "rgba(0,0,0,1)" : obj.color}
          strokeWidth={obj.strokeWidth}
          lineCap="round"
          lineJoin="round"
          tension={INFINITY_LINE_TENSION}
          perfectDrawEnabled={false}
          opacity={1}
          globalCompositeOperation={obj.isEraser ? "destination-out" : "source-over"}
          listening={false}
        />
      );
    }

    if (obj.type === "rect") {
      const isFilled = Boolean(obj.fill);
      return (
        <Rect
          key={`remote-draft-${draft.userUuid}-${obj.id}`}
          x={obj.x}
          y={obj.y}
          width={obj.width}
          height={obj.height}
          rotation={obj.rotation ?? 0}
          stroke={isFilled ? undefined : obj.color}
          strokeWidth={isFilled ? 0 : obj.strokeWidth}
          fill={obj.fill}
          opacity={1}
          listening={false}
        />
      );
    }

    if (obj.type === "ellipse") {
      const radiusX = Math.abs(obj.width / 2);
      const radiusY = Math.abs(obj.height / 2);
      const isFilled = Boolean(obj.fill);
      return (
        <Ellipse
          key={`remote-draft-${draft.userUuid}-${obj.id}`}
          x={obj.x + obj.width / 2}
          y={obj.y + obj.height / 2}
          radiusX={radiusX}
          radiusY={radiusY}
          rotation={obj.rotation ?? 0}
          stroke={isFilled ? undefined : obj.color}
          strokeWidth={isFilled ? 0 : obj.strokeWidth}
          fill={obj.fill}
          opacity={1}
          listening={false}
        />
      );
    }

    return null;
  };

  const shapeAndTextNodes = useMemo(
    () => partitionedObjects.shapeAndTextObjects.map(renderShapeOrText),
    // renderShapeOrText reads the current tool/lock/edit callbacks and should only refresh when those change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [partitionedObjects.shapeAndTextObjects, lockedElementIds, isSelectTool, editingId, handleObjectClick, previewGroupedObjectMove, selectedIds],
  );

  const renderFill = useCallback((obj: InfinityObject) => {
    if (obj.type !== "fill") return null;
    const isLocked = lockedElementIds.has(obj.id);
    return (
      <KonvaFill
        key={obj.id}
        fill={obj}
        isSelectTool={isSelectTool}
        isLocked={isLocked}
        onFillClick={handleObjectClick}
        onFillDragMove={previewGroupedObjectMove}
        onFillDragEnd={onObjectDragEnd}
      />
    );
  }, [handleObjectClick, isSelectTool, lockedElementIds, onObjectDragEnd, previewGroupedObjectMove]);

  const fillNodes = useMemo(
    () => partitionedObjects.fills.map(renderFill),
    [partitionedObjects.fills, renderFill],
  );

  const lineNodes = useMemo(
    () => partitionedObjects.lines.map(renderLine),
    [partitionedObjects.lines, renderLine],
  );

  const remoteEraserDraftNodes = useMemo(
    () =>
      remoteDraftObjects
        .filter((draft) => draft.object.type === "line" && draft.object.isEraser)
        .map(renderRemoteDraftObject),
    [remoteDraftObjects],
  );

  // 텍스트가 단일 선택일 때 Transformer 핸들 정책 — 사이즈 조절 X, 회전 O.
  const onlyTextSelected =
    selectedIds.length > 0 &&
    selectedIds.every((id) => {
      const obj = objectById.get(id);
      return obj?.type === "text";
    });

  const groupedLockedElements = useMemo(() => {
    const lockMap = new Map<string, {
      userUuid: string
      nickname: string
      color: string
      bounds: { x: number; y: number; width: number; height: number }
    }>()

    for (const lock of lockedElements) {
      const object = objectById.get(lock.elementId)
      if (!object) continue
      const bounds = getObjectBounds(object)
      if (!bounds) continue

      const existing = lockMap.get(lock.userUuid)
      if (!existing) {
        lockMap.set(lock.userUuid, {
          userUuid: lock.userUuid,
          nickname: lock.nickname,
          color: lock.color,
          bounds,
        })
        continue
      }

      const minX = Math.min(existing.bounds.x, bounds.x)
      const minY = Math.min(existing.bounds.y, bounds.y)
      const maxX = Math.max(
        existing.bounds.x + existing.bounds.width,
        bounds.x + bounds.width,
      )
      const maxY = Math.max(
        existing.bounds.y + existing.bounds.height,
        bounds.y + bounds.height,
      )
      existing.bounds = {
        x: minX,
        y: minY,
        width: maxX - minX,
        height: maxY - minY,
      }
    }

    return [...lockMap.values()]
  }, [lockedElements, objectById])

  const renderGroupedLockOverlay = (lock: (typeof groupedLockedElements)[number]) => {
    const bounds = lock.bounds;
    const overlayScale = getOverlayScale(scale);
    return (
      <Fragment key={lock.userUuid}>
        <Label
          x={bounds.x + LOCK_LABEL_OFFSET.x * overlayScale}
          y={bounds.y + LOCK_LABEL_OFFSET.y * overlayScale}
          scaleX={overlayScale}
          scaleY={overlayScale}
          listening={false}
        >
          <Tag fill={lock.color} cornerRadius={10} />
          <Text
            text={`${lock.nickname} 편집 중`}
            fill={getReadableTextColor(lock.color)}
            fontSize={12}
            fontStyle="bold"
            padding={7}
          />
        </Label>
      </Fragment>
    );
  };

  return (
    <Stage
      ref={stageRef}
      width={width}
      height={height}
      style={{ cursor: getCursorStyle(tool) }}
      onDragEnd={(e) => {
        if (e.target === e.target.getStage()) {
          onStageDragEnd();
        }
      }}
      onMouseDown={(e) => {
        const stage = e.target.getStage();
        if (!stage) return;
        if (e.evt.button === 1) {
          e.evt.preventDefault();
          isWheelButtonPanningRef.current = true;
          setPointerPanning(true);
          stage.container().style.cursor = "grabbing";
          stage.startDrag(e.evt);
          return;
        }
        const targetIsStage = e.target === stage;
        onStageMouseDown(stage, toolRef.current, targetIsStage);
      }}
      onMouseMove={(e) => {
        const stage = e.target.getStage();
        if (!stage) return;
        onStageMouseMove(stage, toolRef.current);
        const pointerPosition = stage.getRelativePointerPosition();
        if (!pointerPosition) return;
        onCursorMove({
          x: pointerPosition.x,
          y: pointerPosition.y,
          zoom: scaleRef.current,
        });
      }}
      onMouseUp={() => {
        stopWheelButtonPanning();
        onStageMouseUp(toolRef.current);
      }}
      onMouseLeave={() => {
        stopWheelButtonPanning();
        onStageMouseLeave(toolRef.current);
      }}
      onAuxClick={(e: Konva.KonvaEventObject<MouseEvent>) => e.evt.preventDefault()}
      onContextMenu={(e) => {
        e.evt.preventDefault();
        const targetId = e.target.id();
        if (!targetId || toolRef.current !== "select") return;
        requestLayerMenu(targetId, e.evt.clientX, e.evt.clientY);
      }}
      onTouchStart={(e) => {
        clearLayerMenuLongPress();
        const targetId = e.target.id();
        if (!targetId || toolRef.current !== "select") return;
        const touch = e.evt.touches[0];
        if (!touch) return;
        layerMenuLongPressTimerRef.current = window.setTimeout(() => {
          layerMenuLongPressTimerRef.current = null;
          requestLayerMenu(targetId, touch.clientX, touch.clientY);
        }, 520);
      }}
      onTouchMove={clearLayerMenuLongPress}
      onTouchEnd={clearLayerMenuLongPress}
      onTouchCancel={clearLayerMenuLongPress}
      onWheel={onStageWheel}
      onClick={(e) => onStageClick(e, toolRef.current)}
    >
      {/* Layer 0 — 캔버스 배경 */}
      <Layer id={INFINITY_CANVAS_BACKGROUND_LAYER_ID} listening={false}>
        <Rect
          x={-50000}
          y={-50000}
          width={100000}
          height={100000}
          fill="white"
          listening={false}
        />
        <DotGridShape
          width={width}
          height={height}
          scaleRef={scaleRef}
          stagePosRef={stagePosRef}
        />
      </Layer>

      {/* Layer 1 — 도형 + 텍스트 + Transformer (라인보다 아래에 배치) */}
      <Layer>
        {shapeAndTextNodes}

        <Transformer
          ref={transformerRef}
          rotateEnabled={true}
          shouldOverdrawWholeArea={selectedIds.length > 1}
          onDragMove={previewSelectedNodeMoves}
          onDragEnd={commitSelectedNodeMoves}
          onTransform={previewSelectedNodeTransforms}
          onTransformEnd={commitSelectedNodeTransforms}
          enabledAnchors={
            onlyTextSelected
              ? []
              : [
                  "top-left",
                  "top-center",
                  "top-right",
                  "middle-left",
                  "middle-right",
                  "bottom-left",
                  "bottom-center",
                  "bottom-right",
                ]
          }
          boundBoxFunc={(oldBox, newBox) => {
            if (Math.abs(newBox.width) < 5 || Math.abs(newBox.height) < 5) {
              return oldBox;
            }
            if (isShiftDown) {
              const size = Math.max(
                Math.abs(newBox.width),
                Math.abs(newBox.height),
              );
              newBox.width = newBox.width >= 0 ? size : -size;
              newBox.height = newBox.height >= 0 ? size : -size;
            }
            return newBox;
          }}
        />
      </Layer>

      {/* Layer 2 — 라인(완성) + 진행 중 eraser line.
          픽셀 지우개 destination-out scope가 이 Layer로 한정 — 도형/텍스트는 영향 X. */}
      <Layer>
        {fillNodes}
        {lineNodes}
        {remoteEraserDraftNodes}

        <Line
          ref={currentEraserLineRef}
          stroke="rgba(0,0,0,1)"
          strokeWidth={5}
          lineCap="round"
          lineJoin="round"
          globalCompositeOperation="destination-out"
          tension={INFINITY_LINE_TENSION}
          listening={false}
        />
      </Layer>

      {/* Layer 2.5 — 다른 참여자가 그리고 있는 임시 선/도형 */}
      <ImperativeRemoteDraftLayer remoteDraftObjects={remoteDraftObjects} />

      {/* Layer 3 — 진행 중 pen line + preview + cursor + 다중 선택 박스 */}
      <Layer listening={false}>
        <Line
          ref={currentPenLineRef}
          stroke="#000"
          strokeWidth={5}
          lineCap="round"
          lineJoin="round"
          tension={INFINITY_LINE_TENSION}
        />
        <Rect
          ref={previewRectRef}
          stroke="#000"
          strokeWidth={5}
          fill="transparent"
          dash={[6, 4]}
        />
        <ImperativeEllipse
          ref={previewEllipseRef}
          stroke="#000"
          strokeWidth={5}
          fill="transparent"
          dash={[6, 4]}
        />
        <SelectionBox boxRef={selectionBoxRef} />
        <CursorPreview cursorRef={cursorPreviewRef} scale={scale} />
        {groupedLockedElements.map(renderGroupedLockOverlay)}
        <RemoteCursorLayer remoteCursors={remoteCursors} scale={scale} />
      </Layer>
    </Stage>
  );
}
