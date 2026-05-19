import { Ellipse } from "react-konva";
import type Konva from "konva";

import {
  OBJECT_DRAG_DISTANCE,
  getExpandedHitStrokeWidth,
  type KonvaShapeProps,
} from "./shapes.types";

export function KonvaEllipse({
  shape,
  isSelectTool,
  isLocked = false,
  isGroupedSelection = false,
  onShapeClick,
  onShapeDragMove,
  onShapeDragEnd,
  onShapeTransformEnd,
}: KonvaShapeProps) {
  const centerX = shape.x + shape.width / 2;
  const centerY = shape.y + shape.height / 2;
  const radiusX = Math.abs(shape.width / 2);
  const radiusY = Math.abs(shape.height / 2);
  const isFilled = Boolean(shape.fill);

  return (
    <Ellipse
      key={shape.id}
      id={shape.id}
      x={centerX}
      y={centerY}
      radiusX={radiusX}
      radiusY={radiusY}
      rotation={shape.rotation ?? 0}
      stroke={isFilled ? undefined : shape.color}
      strokeWidth={isFilled ? 0 : shape.strokeWidth}
      fill={shape.fill ?? "transparent"}
      hitStrokeWidth={isFilled ? undefined : getExpandedHitStrokeWidth(shape.strokeWidth)}
      dragDistance={OBJECT_DRAG_DISTANCE}
      draggable={isSelectTool && !isLocked}
      onClick={
        isSelectTool
          ? (e) => onShapeClick(shape.id, e.evt.shiftKey)
          : undefined
      }
      onTap={isSelectTool ? () => onShapeClick(shape.id, false) : undefined}
      onDragMove={(e) => {
        const newCenterX = e.target.x();
        const newCenterY = e.target.y();
        onShapeDragMove?.(shape.id, newCenterX - radiusX, newCenterY - radiusY);
      }}
      onDragEnd={(e) => {
        const newCenterX = e.target.x();
        const newCenterY = e.target.y();
        onShapeDragEnd(shape.id, newCenterX - radiusX, newCenterY - radiusY);
      }}
      onTransformEnd={(e) => {
        if (isGroupedSelection) return;
        const node = e.target as Konva.Ellipse;
        const scaleX = node.scaleX();
        const scaleY = node.scaleY();
        node.scaleX(1);
        node.scaleY(1);
        const newRadiusX = node.radiusX() * scaleX;
        const newRadiusY = node.radiusY() * scaleY;
        onShapeTransformEnd(
          shape.id,
          node.x() - newRadiusX,
          node.y() - newRadiusY,
          newRadiusX * 2,
          newRadiusY * 2,
          node.rotation(),
        );
      }}
    />
  );
}
