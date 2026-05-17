import type { InfinityShape } from "../../constants";

export interface KonvaShapeProps {
  shape: InfinityShape;
  isSelectTool: boolean;
  isLocked?: boolean;
  onShapeClick: (id: string, isShift: boolean) => void;
  onShapeDragEnd: (id: string, x: number, y: number) => void;
  onShapeTransformEnd: (
    id: string,
    x: number,
    y: number,
    width: number,
    height: number,
    rotation: number,
  ) => void;
}

export function flattenPoints(points: { x: number; y: number }[]): number[] {
  return points.flatMap((point) => [point.x, point.y]);
}
