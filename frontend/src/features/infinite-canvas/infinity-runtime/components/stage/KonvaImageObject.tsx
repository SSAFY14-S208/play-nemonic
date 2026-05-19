import { useEffect, useState } from "react";
import { Image as KonvaImage } from "react-konva";
import type Konva from "konva";

import type { InfinityImage } from "../../constants";
import { drawImageAlphaHitRegion } from "./imageHitRegion";

interface KonvaImageObjectProps {
  imageObject: InfinityImage;
  isSelectTool?: boolean;
  isLocked?: boolean;
  onImageClick?: (id: string, isShift: boolean) => void;
  onImageDragEnd?: (id: string, x: number, y: number) => void;
  onImageTransformEnd?: (
    id: string,
    x: number,
    y: number,
    width: number,
    height: number,
    rotation: number,
  ) => void;
}

const imageElementCache = new Map<string, HTMLImageElement>();

function resetNodeScale(node: Konva.Image) {
  if (node.scaleX() === 1 && node.scaleY() === 1) return;
  node.scale({ x: 1, y: 1 });
}

export function KonvaImageObject({
  imageObject,
  isSelectTool = false,
  isLocked = false,
  onImageClick,
  onImageDragEnd,
  onImageTransformEnd,
}: KonvaImageObjectProps) {
  const [loadedImage, setLoadedImage] = useState<{
    src: string;
    element: HTMLImageElement;
  } | null>(null);
  const cachedImageElement = imageElementCache.get(imageObject.src) ?? null;
  const loadedImageElement =
    loadedImage?.src === imageObject.src ? loadedImage.element : null;
  const imageElement = cachedImageElement ?? loadedImageElement;

  useEffect(() => {
    const cachedImage = imageElementCache.get(imageObject.src);
    if (cachedImage) return;

    let cancelled = false;
    const image = new window.Image();
    image.crossOrigin = "anonymous";
    image.onload = () => {
      imageElementCache.set(imageObject.src, image);
      if (!cancelled) {
        setLoadedImage({ src: imageObject.src, element: image });
      }
    };
    image.src = imageObject.src;

    return () => {
      cancelled = true;
    };
  }, [imageObject.src]);

  if (!imageElement) return null;

  return (
    <KonvaImage
      id={imageObject.id}
      x={imageObject.x}
      y={imageObject.y}
      width={imageObject.width}
      height={imageObject.height}
      rotation={imageObject.rotation ?? 0}
      image={imageElement}
      listening={isSelectTool}
      perfectDrawEnabled={false}
      hitFunc={(context, shape) => {
        drawImageAlphaHitRegion({
          context,
          imageElement,
          shape,
          width: imageObject.width,
          height: imageObject.height,
        });
      }}
      draggable={isSelectTool && !isLocked}
      onClick={
        isSelectTool
          ? (event) => onImageClick?.(imageObject.id, event.evt.shiftKey)
          : undefined
      }
      onTap={isSelectTool ? () => onImageClick?.(imageObject.id, false) : undefined}
      onDragEnd={(event) => {
        const node = event.target as Konva.Image;
        resetNodeScale(node);
        onImageDragEnd?.(imageObject.id, node.x(), node.y());
      }}
      onTransformEnd={(event) => {
        const node = event.target as Konva.Image;
        const scaleX = node.scaleX();
        const scaleY = node.scaleY();
        resetNodeScale(node);
        onImageTransformEnd?.(
          imageObject.id,
          node.x(),
          node.y(),
          imageObject.width * scaleX,
          imageObject.height * scaleY,
          node.rotation(),
        );
      }}
    />
  );
}
