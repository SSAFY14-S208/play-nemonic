import { Rect } from "react-konva";
import type Konva from "konva";

import type { KonvaShapeProps } from "./shapes.types";

export function KonvaRect({
  shape,
  isSelectTool,
  isLocked = false,
  onShapeClick,
  onShapeDragEnd,
  onShapeTransformEnd,
}: KonvaShapeProps) {
  const isFilled = Boolean(shape.fill);

  return (
    <Rect
      key={shape.id}
      id={shape.id}
      x={shape.x}
      y={shape.y}
      width={shape.width}
      height={shape.height}
      rotation={shape.rotation ?? 0}
      stroke={isFilled ? undefined : shape.color}
      strokeWidth={isFilled ? 0 : shape.strokeWidth}
      fill={shape.fill}
      hitStrokeWidth={isFilled ? undefined : shape.strokeWidth}
      draggable={isSelectTool && !isLocked}
      onClick={
        isSelectTool
          ? (e) => onShapeClick(shape.id, e.evt.shiftKey)
          : undefined
      }
      onTap={isSelectTool ? () => onShapeClick(shape.id, false) : undefined}
      onDragEnd={(e) => {
        onShapeDragEnd(shape.id, e.target.x(), e.target.y());
      }}
      onTransformEnd={(e) => {
        const node = e.target as Konva.Rect;
        const scaleX = node.scaleX();
        const scaleY = node.scaleY();
        node.scaleX(1);
        node.scaleY(1);
        onShapeTransformEnd(
          shape.id,
          node.x(),
          node.y(),
          node.width() * scaleX,
          node.height() * scaleY,
          node.rotation(),
        );
      }}
    />
  );
}
