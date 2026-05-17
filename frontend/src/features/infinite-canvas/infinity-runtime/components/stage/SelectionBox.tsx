import { Rect } from "react-konva";
import type Konva from "konva";

interface SelectionBoxProps {
  boxRef: React.RefObject<Konva.Rect | null>;
}

// 다중 선택 드래그 박스. position/size는 useInfinityEvents가 imperative 갱신.
export function SelectionBox({ boxRef }: SelectionBoxProps) {
  return (
    <Rect
      ref={boxRef}
      x={0}
      y={0}
      width={0}
      height={0}
      visible={false}
      fill="rgba(56, 132, 255, 0.12)"
      stroke="rgba(56, 132, 255, 0.7)"
      strokeWidth={1}
      strokeScaleEnabled={false}
      listening={false}
      perfectDrawEnabled={false}
    />
  );
}
