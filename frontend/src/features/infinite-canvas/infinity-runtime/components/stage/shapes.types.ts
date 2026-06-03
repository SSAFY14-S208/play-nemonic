import type { InfinityShape } from '../..';

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

const flattenedPointCache = new WeakMap<ReadonlyArray<{ x: number; y: number }>, number[]>();

export function flattenPoints(points: { x: number; y: number }[]): number[] {
  const cachedPoints = flattenedPointCache.get(points);
  if (cachedPoints) return cachedPoints;

  const flattenedPoints = new Array<number>(points.length * 2);
  for (let pointIndex = 0; pointIndex < points.length; pointIndex += 1) {
    const point = points[pointIndex];
    const offset = pointIndex * 2;
    flattenedPoints[offset] = point.x;
    flattenedPoints[offset + 1] = point.y;
  }
  flattenedPointCache.set(points, flattenedPoints);
  return flattenedPoints;
}

export function getExpandedHitStrokeWidth(strokeWidth: number): number {
  return Math.max(strokeWidth, MIN_OBJECT_HIT_SIZE);
}
