import { Text } from "react-konva";
import type Konva from "konva";

import {
  INFINITY_TEXT_DEFAULT_FONT_FAMILY,
  type InfinityText,
} from "../../constants";

interface KonvaTextProps {
  textObject: InfinityText;
  isSelectTool: boolean;
  isEditing: boolean;
  isLocked?: boolean;
  onTextClick: (id: string, isShift: boolean) => void;
  onTextDblClick: (id: string) => void;
  onTextDragEnd: (id: string, x: number, y: number) => void;
  onTextTransformEnd: (id: string, x: number, y: number, rotation: number) => void;
}

export function KonvaText({
  textObject,
  isSelectTool,
  isEditing,
  isLocked = false,
  onTextClick,
  onTextDblClick,
  onTextDragEnd,
  onTextTransformEnd,
}: KonvaTextProps) {
  return (
    <Text
      key={textObject.id}
      id={textObject.id}
      x={textObject.x}
      y={textObject.y}
      text={textObject.text}
      fontSize={textObject.fontSize}
      fontFamily={textObject.fontFamily ?? INFINITY_TEXT_DEFAULT_FONT_FAMILY}
      fill={textObject.color}
      rotation={textObject.rotation ?? 0}
      visible={!isEditing}
      draggable={isSelectTool && !isLocked}
      onClick={
        isSelectTool
          ? (e) => onTextClick(textObject.id, e.evt.shiftKey)
          : undefined
      }
      onTap={
        isSelectTool ? () => onTextClick(textObject.id, false) : undefined
      }
      onDblClick={
        isSelectTool ? () => onTextDblClick(textObject.id) : undefined
      }
      onDblTap={
        isSelectTool ? () => onTextDblClick(textObject.id) : undefined
      }
      onDragEnd={(e) => {
        onTextDragEnd(textObject.id, e.target.x(), e.target.y());
      }}
      onTransformEnd={(e) => {
        const node = e.target as Konva.Text;
        // 텍스트는 사이즈 조절 안 함 — scale 1로 reset, fontSize 유지.
        node.scaleX(1);
        node.scaleY(1);
        onTextTransformEnd(textObject.id, node.x(), node.y(), node.rotation());
      }}
    />
  );
}
