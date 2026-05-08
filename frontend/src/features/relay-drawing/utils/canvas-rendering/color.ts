// 색상 파싱 + 픽셀 비교 — bucket fill의 시드 색상 매칭에 사용.

// 알파 거의 0인 픽셀을 "투명"으로 판단할 임계치. JPEG/PNG 압축 등으로 alpha가
// 정확히 0이 아닌 경우가 있어 여유를 둔다.
export const TRANSPARENT_ALPHA_TOLERANCE = 16

// 같은 색을 다른 격자 픽셀에서 측정했을 때 ±N 만큼은 같은 색으로 본다.
// 안티앨리어싱 경계를 채우기에 포함할지를 결정하는 핵심 파라미터.
export const COLOR_MATCH_TOLERANCE = 12

export function parseHexColor(hexColor: string) {
  const normalizedHex = hexColor.replace('#', '')
  const red = Number.parseInt(normalizedHex.slice(0, 2), 16)
  const green = Number.parseInt(normalizedHex.slice(2, 4), 16)
  const blue = Number.parseInt(normalizedHex.slice(4, 6), 16)

  return { red, green, blue, alpha: 255 }
}

export function isPixelMatchingTarget(
  imageData: Uint8ClampedArray,
  pixelOffset: number,
  targetColor: { red: number; green: number; blue: number; alpha: number },
) {
  const pixelAlpha = imageData[pixelOffset + 3]

  if (targetColor.alpha <= TRANSPARENT_ALPHA_TOLERANCE) {
    return pixelAlpha <= TRANSPARENT_ALPHA_TOLERANCE
  }

  return (
    Math.abs(imageData[pixelOffset] - targetColor.red) <= COLOR_MATCH_TOLERANCE &&
    Math.abs(imageData[pixelOffset + 1] - targetColor.green) <= COLOR_MATCH_TOLERANCE &&
    Math.abs(imageData[pixelOffset + 2] - targetColor.blue) <= COLOR_MATCH_TOLERANCE &&
    Math.abs(pixelAlpha - targetColor.alpha) <= COLOR_MATCH_TOLERANCE
  )
}
