import { Circle } from "react-konva";
import type Konva from "konva";

interface CursorPreviewProps {
  cursorRef: React.RefObject<Konva.Circle | null>;
}

// 도구별 마우스 커서 미리보기. fill/stroke/radius/위치는 useInfinityEvents가
// ref로 imperative 갱신 — React 리렌더 없이 mousemove 추적.
export function CursorPreview({ cursorRef }: CursorPreviewProps) {
  return (
    <Circle
      ref={cursorRef}
      fill="white"
      stroke="black"
      strokeWidth={1}
      strokeScaleEnabled={false}
      listening={false}
      perfectDrawEnabled={false}
    />
  );
}
