import { useEffect, useState } from "react";
import { Image as KonvaImage } from "react-konva";

import type { InfinityFill } from "../../constants";

interface KonvaFillProps {
  fill: InfinityFill;
}

export function KonvaFill({ fill }: KonvaFillProps) {
  const [imageElement, setImageElement] = useState<HTMLImageElement | null>(null);

  useEffect(() => {
    let cancelled = false;
    const image = new window.Image();
    image.onload = () => {
      if (!cancelled) setImageElement(image);
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
