import { Circle } from "react-konva";
import type Konva from "konva";

interface CursorPreviewProps {
  cursorRef: React.RefObject<Konva.Circle | null>;
  scale: number;
}

// 도구별 마우스 커서 미리보기. fill/stroke/radius/위치는 useInfinityEvents가
// ref로 imperative 갱신 — React 리렌더 없이 mousemove 추적.
export function CursorPreview({ cursorRef, scale }: CursorPreviewProps) {
  const overlayScale = scale > 0 ? 1 / scale : 1;

  return (
    <Circle
      ref={cursorRef}
      scaleX={overlayScale}
      scaleY={overlayScale}
      fill="white"
      stroke="black"
      strokeWidth={1}
      strokeScaleEnabled={false}
      listening={false}
      perfectDrawEnabled={false}
    />
  );
}
