import { useEffect, useState } from "react";
import { Image as KonvaImage } from "react-konva";

import type { InfinityFill } from '../..';
import { drawImageAlphaHitRegion } from "./imageHitRegion";
import { OBJECT_DRAG_DISTANCE } from "./shapes.types";

interface KonvaFillProps {
  fill: InfinityFill;
  isSelectTool?: boolean;
  isSelected?: boolean;
  isLocked?: boolean;
  onFillClick?: (id: string, isShift: boolean) => void;
  onFillDragMove?: (id: string, x: number, y: number) => void;
  onFillDragEnd?: (id: string, x: number, y: number) => void;
}

const fillImageCache = new Map<string, HTMLImageElement>();

export function KonvaFill({
  fill,
  isSelectTool = false,
  isSelected = false,
  isLocked = false,
  onFillClick,
  onFillDragMove,
  onFillDragEnd,
}: KonvaFillProps) {
  const [loadedImage, setLoadedImage] = useState<{
    imageDataUrl: string;
    element: HTMLImageElement;
  } | null>(null);
  const cachedImageElement = fillImageCache.get(fill.imageDataUrl) ?? null;
  const loadedImageElement =
    loadedImage?.imageDataUrl === fill.imageDataUrl ? loadedImage.element : null;
  const imageElement = cachedImageElement ?? loadedImageElement;

  useEffect(() => {
    const cachedImage = fillImageCache.get(fill.imageDataUrl);
    if (cachedImage) {
      return;
    }

    let cancelled = false;
    const image = new window.Image();
    image.onload = () => {
      fillImageCache.set(fill.imageDataUrl, image);
      if (!cancelled) {
        setLoadedImage({ imageDataUrl: fill.imageDataUrl, element: image });
      }
    };
    image.src = fill.imageDataUrl;

    return () => {
      cancelled = true;
    };
  }, [fill.imageDataUrl]);

  if (!imageElement) return null;

  return (
    <KonvaImage
      id={fill.id}
      x={fill.x}
      y={fill.y}
      width={fill.width}
      height={fill.height}
      image={imageElement}
      listening={isSelectTool}
      perfectDrawEnabled={false}
      hitFunc={(context, shape) => {
        if (isSelected) {
          context.beginPath();
          context.rect(0, 0, fill.width, fill.height);
          context.closePath();
          context.fillStrokeShape(shape);
          return;
        }
        drawImageAlphaHitRegion({
          context,
          imageElement,
          shape,
          width: fill.width,
          height: fill.height,
        });
      }}
      dragDistance={OBJECT_DRAG_DISTANCE}
      draggable={isSelectTool && !isLocked}
      onClick={
        isSelectTool
          ? (e) => onFillClick?.(fill.id, e.evt.shiftKey)
          : undefined
      }
      onTap={isSelectTool ? () => onFillClick?.(fill.id, false) : undefined}
      onDragMove={(event) => {
        onFillDragMove?.(fill.id, event.target.x(), event.target.y());
      }}
      onDragEnd={(event) => {
        onFillDragEnd?.(fill.id, event.target.x(), event.target.y());
      }}
    />
  );
}
