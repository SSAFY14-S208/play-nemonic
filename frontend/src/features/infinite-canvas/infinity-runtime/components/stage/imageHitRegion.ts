import type Konva from "konva";

const HIT_ALPHA_THRESHOLD = 24;
const HIT_GRID_TARGET_CELLS = 140;
const HIT_GRID_MIN_SIZE = 2;
const HIT_GRID_MAX_SIZE = 5;
const hitAlphaDataCache = new WeakMap<
  HTMLImageElement,
  { width: number; height: number; pixels: Uint8ClampedArray } | null
>();

function getHitAlphaData(imageElement: HTMLImageElement) {
  if (hitAlphaDataCache.has(imageElement)) {
    return hitAlphaDataCache.get(imageElement) ?? null;
  }

  const sourceWidth = imageElement.naturalWidth || imageElement.width;
  const sourceHeight = imageElement.naturalHeight || imageElement.height;
  if (sourceWidth <= 0 || sourceHeight <= 0) {
    hitAlphaDataCache.set(imageElement, null);
    return null;
  }

  try {
    const canvas = document.createElement("canvas");
    canvas.width = sourceWidth;
    canvas.height = sourceHeight;
    const context = canvas.getContext("2d", { willReadFrequently: true });
    if (!context) {
      hitAlphaDataCache.set(imageElement, null);
      return null;
    }
    context.drawImage(imageElement, 0, 0, sourceWidth, sourceHeight);
    const imageData = context.getImageData(0, 0, sourceWidth, sourceHeight);
    const alphaData = {
      width: sourceWidth,
      height: sourceHeight,
      pixels: imageData.data,
    };
    hitAlphaDataCache.set(imageElement, alphaData);
    return alphaData;
  } catch {
    hitAlphaDataCache.set(imageElement, null);
    return null;
  }
}

export function drawImageAlphaHitRegion({
  context,
  imageElement,
  shape,
  height,
  width,
}: {
  context: Konva.Context;
  imageElement: HTMLImageElement;
  shape: Konva.Shape;
  height: number;
  width: number;
}) {
  const alphaData = getHitAlphaData(imageElement);
  if (!alphaData || width <= 0 || height <= 0) {
    context.beginPath();
    context.rect(0, 0, width, height);
    context.closePath();
    context.fillStrokeShape(shape);
    return;
  }

  const gridSize = Math.min(
    HIT_GRID_MAX_SIZE,
    Math.max(HIT_GRID_MIN_SIZE, Math.ceil(Math.max(width, height) / HIT_GRID_TARGET_CELLS)),
  );
  const scaleX = alphaData.width / width;
  const scaleY = alphaData.height / height;

  context.beginPath();
  for (let y = 0; y < height; y += gridSize) {
    for (let x = 0; x < width; x += gridSize) {
      const sourceX = Math.min(alphaData.width - 1, Math.floor((x + gridSize / 2) * scaleX));
      const sourceY = Math.min(alphaData.height - 1, Math.floor((y + gridSize / 2) * scaleY));
      const alpha = alphaData.pixels[(sourceY * alphaData.width + sourceX) * 4 + 3] ?? 0;
      if (alpha <= HIT_ALPHA_THRESHOLD) continue;

      context.rect(
        x,
        y,
        Math.min(gridSize, width - x),
        Math.min(gridSize, height - y),
      );
    }
  }
  context.closePath();
  context.fillStrokeShape(shape);
}
