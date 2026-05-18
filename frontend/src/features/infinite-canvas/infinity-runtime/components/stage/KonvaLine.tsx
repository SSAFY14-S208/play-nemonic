import { Line } from "react-konva";

import { INFINITY_LINE_TENSION, type InfinityLine } from "../../constants";
import { flattenPoints } from "./shapes.types";

interface KonvaLineProps {
  line: InfinityLine;
  isSelectTool?: boolean;
  isLocked?: boolean;
  onLineClick?: (id: string, isShift: boolean) => void;
}

export function KonvaLine({
  line,
  isSelectTool = false,
  isLocked = false,
  onLineClick,
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
      draggable={isSelectTool && !isLocked}
      onClick={
        isSelectTool
          ? (e) => onLineClick?.(line.id, e.evt.shiftKey)
          : undefined
      }
      onTap={isSelectTool ? () => onLineClick?.(line.id, false) : undefined}
    />
  );
}
