import { Line } from "react-konva";

import type { InfinityLine } from "../../constants";
import { flattenPoints } from "./shapes.types";

interface KonvaLineProps {
  line: InfinityLine;
}

export function KonvaLine({ line }: KonvaLineProps) {
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
      tension={0.3}
    />
  );
}
