import { useEffect, useState } from "react";
import { Image as KonvaImage } from "react-konva";

import type { InfinityFill } from "../../constants";

interface KonvaFillProps {
  fill: InfinityFill;
}

const fillImageCache = new Map<string, HTMLImageElement>();

export function KonvaFill({ fill }: KonvaFillProps) {
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
      listening={false}
      perfectDrawEnabled={false}
    />
  );
}
