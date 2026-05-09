const PROXYABLE_IMAGE_PROTOCOLS = new Set(['http:', 'https:'])

export function getDisplayImageUrl(imageUrl: string | null | undefined) {
  if (!imageUrl) return null

  try {
    const parsedImageUrl = new URL(imageUrl)

    if (PROXYABLE_IMAGE_PROTOCOLS.has(parsedImageUrl.protocol)) {
      return `/api/image-proxy?url=${encodeURIComponent(imageUrl)}`
    }
  } catch {
    return imageUrl
  }

  return imageUrl
}
