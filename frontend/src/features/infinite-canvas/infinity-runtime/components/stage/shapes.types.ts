import type { InfinityShape } from "../../constants";

export const MIN_OBJECT_HIT_SIZE = 12;
export const OBJECT_DRAG_DISTANCE = 1;

export interface KonvaShapeProps {
  shape: InfinityShape;
  isSelectTool: boolean;
  isSelected?: boolean;
  isLocked?: boolean;
  isGroupedSelection?: boolean;
  onShapeClick: (id: string, isShift: boolean) => void;
  onShapeDragMove?: (id: string, x: number, y: number) => void;
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

export function getExpandedHitStrokeWidth(strokeWidth: number): number {
  return Math.max(strokeWidth, MIN_OBJECT_HIT_SIZE);
}
