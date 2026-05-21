import { Shape } from "react-konva";
import type Konva from "konva";

const DOT_SPACING = 30;
const DOT_RADIUS = 1.5;
const DOT_COLOR = "#c8d8ed";
// 화면상 dot 간격이 이 값보다 작아지면 canvas 좌표 spacing을 2배씩 키워서
// dot 개수가 폭증하지 않도록 한다. 줌 아웃 시 sceneFunc 비용 폭주 방지.
const MIN_SCREEN_SPACING = 20;
// safety guard — spacing 2배 루프가 무한히 돌지 않도록 상한.
const MAX_SPACING_DOUBLINGS = 16;

interface DotGridShapeProps {
  width: number;
  height: number;
  scaleRef: React.RefObject<number>;
  stagePosRef: React.RefObject<{ x: number; y: number }>;
}

// 도트 그리드 sceneFunc — viewport 안에 들어오는 도트만 단일 Shape로 한 번에 그림.
// <Circle> 노드를 도트 개수만큼 만드는 대신, native canvas 2D API(arc)를 직접 호출해
// React/Konva 객체 관리 비용을 1개분으로 압축한다.
// viewport ref를 직접 읽으므로 React 리렌더 없이 batchDraw만으로 최신값이 반영된다.
export function DotGridShape({
  width,
  height,
  scaleRef,
  stagePosRef,
}: DotGridShapeProps) {
  const drawDotGrid = (context: Konva.Context, shape: Konva.Shape) => {
    const scale = scaleRef.current;
    const stagePos = stagePosRef.current;
    if (
      width <= 0 ||
      height <= 0 ||
      scale <= 0 ||
      !Number.isFinite(width) ||
      !Number.isFinite(height) ||
      !Number.isFinite(scale) ||
      !Number.isFinite(stagePos.x) ||
      !Number.isFinite(stagePos.y)
    ) {
      return;
    }

    let effectiveSpacing = DOT_SPACING;
    let doublings = 0;
    while (
      effectiveSpacing * scale < MIN_SCREEN_SPACING &&
      doublings < MAX_SPACING_DOUBLINGS
    ) {
      effectiveSpacing *= 2;
      doublings += 1;
    }

    const viewLeft = -stagePos.x / scale;
    const viewTop = -stagePos.y / scale;
    const viewRight = viewLeft + width / scale;
    const viewBottom = viewTop + height / scale;

    const startX = Math.floor(viewLeft / effectiveSpacing) * effectiveSpacing;
    const startY = Math.floor(viewTop / effectiveSpacing) * effectiveSpacing;
    const endX = Math.ceil(viewRight / effectiveSpacing) * effectiveSpacing;
    const endY = Math.ceil(viewBottom / effectiveSpacing) * effectiveSpacing;

    context.beginPath();
    for (let dotX = startX; dotX <= endX; dotX += effectiveSpacing) {
      for (let dotY = startY; dotY <= endY; dotY += effectiveSpacing) {
        context.moveTo(dotX + DOT_RADIUS, dotY);
        context.arc(dotX, dotY, DOT_RADIUS, 0, Math.PI * 2);
      }
    }
    context.fillStyle = DOT_COLOR;
    context.fill();
    context.fillStrokeShape(shape);
  };

  return (
    <Shape
      sceneFunc={drawDotGrid}
      listening={false}
      perfectDrawEnabled={false}
    />
  );
}
