import { Line } from "react-konva";

import { INFINITY_LINE_TENSION, type InfinityLine } from "../../constants";
import {
  OBJECT_DRAG_DISTANCE,
  flattenPoints,
  getExpandedHitStrokeWidth,
} from "./shapes.types";

interface KonvaLineProps {
  line: InfinityLine;
  isSelectTool?: boolean;
  isLocked?: boolean;
  onLineClick?: (id: string, isShift: boolean) => void;
  onLineDragMove?: (id: string, deltaX: number, deltaY: number) => void;
  onLineDragEnd?: (id: string, deltaX: number, deltaY: number) => void;
}

export function KonvaLine({
  line,
  isSelectTool = false,
  isLocked = false,
  onLineClick,
  onLineDragMove,
  onLineDragEnd,
}: KonvaLineProps) {
  return (
    <Line
      id={line.id}
      points={flattenPoints(line.points)}
      stroke={line.isEraser ? "rgba(0,0,0,1)" : line.color}
      strokeWidth={line.strokeWidth}
      lineCap="round"
      lineJoin="round"
      perfectDrawEnabled={false}
      globalCompositeOperation={
        line.isEraser ? "destination-out" : "source-over"
      }
      tension={INFINITY_LINE_TENSION}
      hitStrokeWidth={getExpandedHitStrokeWidth(line.strokeWidth)}
      dragDistance={OBJECT_DRAG_DISTANCE}
      draggable={isSelectTool && !isLocked}
      onClick={
        isSelectTool
          ? (e) => onLineClick?.(line.id, e.evt.shiftKey)
          : undefined
      }
      onTap={isSelectTool ? () => onLineClick?.(line.id, false) : undefined}
      onDragMove={(event) => {
        onLineDragMove?.(line.id, event.target.x(), event.target.y());
      }}
      onDragEnd={(event) => {
        const deltaX = event.target.x();
        const deltaY = event.target.y();
        event.target.x(0);
        event.target.y(0);
        onLineDragEnd?.(line.id, deltaX, deltaY);
      }}
    />
  );
}
