'use client'

import { Fragment, useEffect, useRef, useState } from 'react'
import { Stage, Layer, Rect, Ellipse, Line, Transformer, Label, Tag, Text, Circle, Path } from 'react-konva'
import type Konva from 'konva'

import type { InfinityLine, InfinityObject, InfinityShape, InfinityText as InfinityTextObject, InfinityToolKey } from '../constants'
import { INFINITY_PARTICIPANT_ACCENTS } from '../constants'
import type { useInfinityDrawing } from '../hooks'
import {
  CursorPreview,
  DotGridShape,
  KonvaEllipse,
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
}

const REMOTE_CURSOR_SMOOTHING = 0.28
const REMOTE_CURSOR_SETTLE_DISTANCE = 0.35
const REMOTE_CURSOR_PATH = 'M0 0 L0 22 L6 16 L10 26 L14 24 L10 15 L19 15 Z'
const IDENTITY_DASHES = [
  undefined,
  [8, 4],
  [2, 4],
  [10, 3, 2, 3],
  [1, 5],
  [6, 2, 2, 2],
  [12, 4],
  [3, 3],
  [8, 2, 2, 2],
  [1, 3],
] as const

function getParticipantAccent(identityIndex: number) {
  return INFINITY_PARTICIPANT_ACCENTS[Math.abs(identityIndex) % INFINITY_PARTICIPANT_ACCENTS.length]
}

function getParticipantDash(identityIndex: number) {
  const dash = IDENTITY_DASHES[Math.abs(identityIndex) % IDENTITY_DASHES.length]
  return dash ? [...dash] : undefined
}

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
  const [smoothCursors, setSmoothCursors] = useState<SmoothRemoteCursorView[]>(() =>
    remoteCursors.map((cursor) => ({
      ...cursor,
      targetX: cursor.x,
      targetY: cursor.y,
    })),
  )

  useEffect(() => {
    targetCursorsRef.current = remoteCursors
  }, [remoteCursors])

  useEffect(() => {
    let animationFrameId = 0

    const tick = () => {
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
            return {
              ...targetCursor,
              targetX: targetCursor.x,
              targetY: targetCursor.y,
            }
          }

          const deltaX = targetCursor.x - currentCursor.x
          const deltaY = targetCursor.y - currentCursor.y
          const nextX =
            Math.abs(deltaX) < REMOTE_CURSOR_SETTLE_DISTANCE
              ? targetCursor.x
              : currentCursor.x + deltaX * REMOTE_CURSOR_SMOOTHING
          const nextY =
            Math.abs(deltaY) < REMOTE_CURSOR_SETTLE_DISTANCE
              ? targetCursor.y
              : currentCursor.y + deltaY * REMOTE_CURSOR_SMOOTHING

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
      animationFrameId = window.requestAnimationFrame(tick)
    }

    animationFrameId = window.requestAnimationFrame(tick)
    return () => window.cancelAnimationFrame(animationFrameId)
  }, [])

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
}: InfinityCanvasStageProps) {
  const {
    objects,
    selectedIds,
    tool,
    textEditor,
    viewport: { scaleRef, stagePosRef },
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
      onTextDblClick,
    },
  } = drawing;

  const toolRef = useRef<InfinityToolKey>(tool);
  useEffect(() => {
    toolRef.current = tool;
  }, [tool]);

  const transformerRef = useRef<Konva.Transformer>(null);
  const smoothRemoteCursors = useSmoothRemoteCursors(remoteCursors);

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
  const handleObjectClick = (id: string, isShift: boolean) => {
    onObjectClick(id, isShift, toolRef.current);
  };

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
          onShapeClick={handleObjectClick}
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
          onShapeClick={handleObjectClick}
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
          onTextClick={handleObjectClick}
          onTextDblClick={onTextDblClick}
          onTextDragEnd={onObjectDragEnd}
          onTextTransformEnd={onTextTransformEnd}
        />
      );
    }
    return null;
  };

  const renderLine = (obj: InfinityObject) => {
    if (obj.type !== "line") return null;
    return <KonvaLine key={obj.id} line={obj} />;
  };

  const renderRemoteDraftObject = (draft: InfinityRemoteDraftObjectView) => {
    const obj = draft.object

    if (obj.type === "line") {
      if (obj.isEraser) return null;
      const points = obj.points.flatMap((point) => [point.x, point.y])
      return (
        <Line
          key={`remote-draft-${draft.userUuid}-${obj.id}`}
          points={points}
          stroke={obj.color}
          strokeWidth={obj.strokeWidth}
          lineCap="round"
          lineJoin="round"
          tension={0.3}
          opacity={1}
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
          fill={obj.fill ?? "transparent"}
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
          fill={obj.fill ?? "transparent"}
          opacity={1}
          listening={false}
        />
      );
    }

    return null;
  };

  // 텍스트가 단일 선택일 때 Transformer 핸들 정책 — 사이즈 조절 X, 회전 O.
  const onlyTextSelected =
    selectedIds.length > 0 &&
    selectedIds.every((id) => {
      const obj = objects.find((o) => o.id === id);
      return obj?.type === "text";
    });

  const renderLockOverlay = (lock: InfinityLockedElementView) => {
    const object = objects.find((candidate) => candidate.id === lock.elementId);
    if (!object) return null;
    const bounds = getObjectBounds(object);
    if (!bounds) return null;
    const accentColor = getParticipantAccent(lock.identityIndex);
    const identityDash = getParticipantDash(lock.identityIndex) ?? [8, 5];

    return (
      <Fragment key={lock.elementId}>
        <Rect
          x={bounds.x - 8}
          y={bounds.y - 8}
          width={bounds.width + 16}
          height={bounds.height + 16}
          stroke={lock.color}
          strokeWidth={3}
          dash={identityDash}
          cornerRadius={8}
          listening={false}
        />
        <Rect
          x={bounds.x - 12}
          y={bounds.y - 12}
          width={bounds.width + 24}
          height={bounds.height + 24}
          stroke={accentColor}
          strokeWidth={2}
          dash={identityDash}
          cornerRadius={10}
          listening={false}
        />
        <Label x={bounds.x - 8} y={bounds.y - 34} listening={false}>
          <Tag fill={lock.color} stroke={accentColor} strokeWidth={2} cornerRadius={10} />
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

  const renderRemoteCursor = (cursor: SmoothRemoteCursorView) => {
    const accentColor = getParticipantAccent(cursor.identityIndex);
    const identityDash = getParticipantDash(cursor.identityIndex);

    return (
      <Fragment key={cursor.userUuid}>
        <Path
          x={cursor.x}
          y={cursor.y}
          data={REMOTE_CURSOR_PATH}
          fill={cursor.color}
          stroke="#ffffff"
          strokeWidth={2.4}
          shadowColor="rgba(45,58,85,0.2)"
          shadowBlur={8}
          shadowOffset={{ x: 0, y: 3 }}
          listening={false}
        />
        <Circle
          x={cursor.x}
          y={cursor.y}
          radius={4}
          fill="#ffffff"
          stroke={accentColor}
          strokeWidth={2}
          dash={identityDash}
          listening={false}
        />
        <Label x={cursor.x + 18} y={cursor.y + 24} listening={false}>
          <Tag
            fill={cursor.color}
            stroke={accentColor}
            strokeWidth={2}
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
      onMouseUp={() => onStageMouseUp(toolRef.current)}
      onMouseLeave={() => onStageMouseLeave(toolRef.current)}
      onWheel={onStageWheel}
      onClick={(e) => onStageClick(e, toolRef.current)}
    >
      {/* Layer 0 — 캔버스 배경 */}
      <Layer listening={false}>
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
        {objects.map(renderShapeOrText)}

        <Transformer
          ref={transformerRef}
          rotateEnabled={true}
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
        {objects.map(renderLine)}

        <Line
          ref={currentEraserLineRef}
          points={[]}
          visible={false}
          stroke="rgba(0,0,0,1)"
          strokeWidth={5}
          lineCap="round"
          lineJoin="round"
          globalCompositeOperation="destination-out"
          tension={0.3}
          listening={false}
        />
      </Layer>

      {/* Layer 2.5 — 다른 참여자가 그리고 있는 임시 선/도형 */}
      <Layer listening={false}>
        {remoteDraftObjects.map(renderRemoteDraftObject)}
      </Layer>

      {/* Layer 3 — 진행 중 pen line + preview + cursor + 다중 선택 박스 */}
      <Layer listening={false}>
        <Line
          ref={currentPenLineRef}
          points={[]}
          visible={false}
          stroke="#000"
          strokeWidth={5}
          lineCap="round"
          lineJoin="round"
          tension={0.3}
        />
        <Rect
          ref={previewRectRef}
          visible={false}
          stroke="#000"
          strokeWidth={5}
          fill="transparent"
          dash={[6, 4]}
        />
        <Ellipse
          ref={previewEllipseRef}
          visible={false}
          radiusX={0}
          radiusY={0}
          stroke="#000"
          strokeWidth={5}
          fill="transparent"
          dash={[6, 4]}
        />
        <SelectionBox boxRef={selectionBoxRef} />
        <CursorPreview cursorRef={cursorPreviewRef} />
        {lockedElements.map(renderLockOverlay)}
        {smoothRemoteCursors.map(renderRemoteCursor)}
      </Layer>
    </Stage>
  );
}
