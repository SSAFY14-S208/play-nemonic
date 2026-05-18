// 백엔드의 `ocrCategories`는 Swagger 명세상 "JSON 문자열"이지만 실제 응답에서는
// 문자열·배열·null 등 다양한 형태로 내려온다. UI가 `.map()`을 안전하게 쓸 수 있도록
// 항상 `string[]`로 변환한다.

export function normalizeOcrCategories(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is string => typeof item === 'string')
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return []
    try {
      const parsed = JSON.parse(trimmed)
      if (Array.isArray(parsed)) {
        return parsed.filter((item): item is string => typeof item === 'string')
      }
      if (typeof parsed === 'string' && parsed.length > 0) return [parsed]
    } catch {
      return trimmed
        .split(',')
        .map((segment) => segment.trim())
        .filter(Boolean)
    }
  }
  return []
}
