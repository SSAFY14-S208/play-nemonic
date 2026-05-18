import { Line } from "react-konva";

import { INFINITY_LINE_TENSION, type InfinityLine } from "../../constants";
import { flattenPoints } from "./shapes.types";

interface KonvaLineProps {
  line: InfinityLine;
  isSelectTool?: boolean;
  isLocked?: boolean;
  onLineClick?: (id: string, isShift: boolean) => void;
  onLineDragEnd?: (id: string, deltaX: number, deltaY: number) => void;
}

export function KonvaLine({
  line,
  isSelectTool = false,
  isLocked = false,
  onLineClick,
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
      globalCompositeOperation={
        line.isEraser ? "destination-out" : "source-over"
      }
      tension={INFINITY_LINE_TENSION}
      hitStrokeWidth={line.strokeWidth}
      draggable={isSelectTool && !isLocked}
      onClick={
        isSelectTool
          ? (e) => onLineClick?.(line.id, e.evt.shiftKey)
          : undefined
      }
      onTap={isSelectTool ? () => onLineClick?.(line.id, false) : undefined}
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
