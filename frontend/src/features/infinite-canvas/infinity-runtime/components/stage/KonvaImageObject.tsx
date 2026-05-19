import { useEffect, useState } from "react";
import { Image as KonvaImage } from "react-konva";
import type Konva from "konva";

import type { InfinityImage } from "../../constants";
import { drawImageAlphaHitRegion } from "./imageHitRegion";

interface KonvaImageObjectProps {
  imageObject: InfinityImage;
  isSelectTool?: boolean;
  isLocked?: boolean;
  isGroupedSelection?: boolean;
  onImageClick?: (id: string, isShift: boolean) => void;
  onImageDragMove?: (id: string, x: number, y: number) => void;
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
const stickerImageElementCache = new Map<string, HTMLImageElement>();
const STICKER_BACKGROUND_ALPHA_THRESHOLD = 40;
const STICKER_BACKGROUND_WHITE_THRESHOLD = 244;
const STICKER_BACKGROUND_WHITE_VARIANCE = 18;

function resetNodeScale(node: Konva.Image) {
  if (node.scaleX() === 1 && node.scaleY() === 1) return;
  node.scale({ x: 1, y: 1 });
}

function isAiStickerObject(imageObject: InfinityImage) {
  return (
    imageObject.metadata?.source === "ai_sticker" ||
    imageObject.id.startsWith("ai-sticker-") ||
    imageObject.objectKey?.includes("/ai-stickers/") === true
  );
}

function isStickerBackgroundPixel(pixels: Uint8ClampedArray, pixelIndex: number) {
  const offset = pixelIndex * 4;
  const red = pixels[offset] ?? 0;
  const green = pixels[offset + 1] ?? 0;
  const blue = pixels[offset + 2] ?? 0;
  const alpha = pixels[offset + 3] ?? 0;
  if (alpha <= STICKER_BACKGROUND_ALPHA_THRESHOLD) return true;

  const maxChannel = Math.max(red, green, blue);
  const minChannel = Math.min(red, green, blue);
  return (
    red >= STICKER_BACKGROUND_WHITE_THRESHOLD &&
    green >= STICKER_BACKGROUND_WHITE_THRESHOLD &&
    blue >= STICKER_BACKGROUND_WHITE_THRESHOLD &&
    maxChannel - minChannel <= STICKER_BACKGROUND_WHITE_VARIANCE
  );
}

async function loadImageFromSrc(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new window.Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("Failed to load sanitized sticker image."));
    image.src = src;
  });
}

async function createSanitizedStickerImage(imageElement: HTMLImageElement) {
  const sourceWidth = imageElement.naturalWidth || imageElement.width;
  const sourceHeight = imageElement.naturalHeight || imageElement.height;
  if (sourceWidth <= 0 || sourceHeight <= 0) return imageElement;

  try {
    const canvas = document.createElement("canvas");
    canvas.width = sourceWidth;
    canvas.height = sourceHeight;
    const context = canvas.getContext("2d", { willReadFrequently: true });
    if (!context) return imageElement;

    context.drawImage(imageElement, 0, 0, sourceWidth, sourceHeight);
    const imageData = context.getImageData(0, 0, sourceWidth, sourceHeight);
    const { data } = imageData;
    const pixelCount = sourceWidth * sourceHeight;
    const visited = new Uint8Array(pixelCount);
    const queue = new Int32Array(pixelCount);
    let queueStart = 0;
    let queueEnd = 0;

    const enqueue = (pixelIndex: number) => {
      if (visited[pixelIndex]) return;
      if (!isStickerBackgroundPixel(data, pixelIndex)) return;
      visited[pixelIndex] = 1;
      queue[queueEnd] = pixelIndex;
      queueEnd += 1;
    };

    for (let x = 0; x < sourceWidth; x += 1) {
      enqueue(x);
      enqueue((sourceHeight - 1) * sourceWidth + x);
    }
    for (let y = 1; y < sourceHeight - 1; y += 1) {
      enqueue(y * sourceWidth);
      enqueue(y * sourceWidth + sourceWidth - 1);
    }

    while (queueStart < queueEnd) {
      const pixelIndex = queue[queueStart];
      queueStart += 1;
      const offset = pixelIndex * 4;
      data[offset + 3] = 0;

      const x = pixelIndex % sourceWidth;
      if (x > 0) enqueue(pixelIndex - 1);
      if (x < sourceWidth - 1) enqueue(pixelIndex + 1);
      if (pixelIndex >= sourceWidth) enqueue(pixelIndex - sourceWidth);
      if (pixelIndex < pixelCount - sourceWidth) enqueue(pixelIndex + sourceWidth);
    }

    context.putImageData(imageData, 0, 0);
    return await loadImageFromSrc(canvas.toDataURL("image/png"));
  } catch {
    return imageElement;
  }
}

export function KonvaImageObject({
  imageObject,
  isSelectTool = false,
  isLocked = false,
  isGroupedSelection = false,
  onImageClick,
  onImageDragMove,
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
  const shouldSanitizeSticker = isAiStickerObject(imageObject);
  const cachedStickerImageElement =
    shouldSanitizeSticker ? stickerImageElementCache.get(imageObject.src) ?? null : null;
  const imageElement = cachedStickerImageElement ?? cachedImageElement ?? loadedImageElement;

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

  useEffect(() => {
    if (!shouldSanitizeSticker || !imageElement || stickerImageElementCache.has(imageObject.src)) {
      return;
    }

    let cancelled = false;
    void createSanitizedStickerImage(imageElement).then((sanitizedImage) => {
      stickerImageElementCache.set(imageObject.src, sanitizedImage);
      if (!cancelled) {
        setLoadedImage({ src: imageObject.src, element: sanitizedImage });
      }
    });

    return () => {
      cancelled = true;
    };
  }, [imageElement, imageObject.src, shouldSanitizeSticker]);

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
      onDragMove={(event) => {
        const node = event.target as Konva.Image;
        onImageDragMove?.(imageObject.id, node.x(), node.y());
      }}
      onDragEnd={(event) => {
        const node = event.target as Konva.Image;
        resetNodeScale(node);
        onImageDragEnd?.(imageObject.id, node.x(), node.y());
      }}
      onTransformEnd={(event) => {
        if (isGroupedSelection) return;
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
