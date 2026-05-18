const GIF_MIME_TYPE = 'image/gif'
const JPEG_MIME_TYPE = 'image/jpeg'
const PNG_MIME_TYPE = 'image/png'
const WEBP_MIME_TYPE = 'image/webp'

export type ExternalImageShareResult =
  | 'native-file'
  | 'native-url'
  | 'copied-gif-link'
  | 'copied-image'
  | 'copied-image-link'
  | 'cancelled'

function toAbsoluteShareUrl(imageUrl: string) {
  if (typeof window === 'undefined') return imageUrl

  return new URL(imageUrl, window.location.origin).toString()
}

function getShareImageExtension(mimeType: string, imageUrl: string) {
  if (mimeType === GIF_MIME_TYPE) return 'gif'
  if (mimeType === JPEG_MIME_TYPE) return 'jpg'
  if (mimeType === WEBP_MIME_TYPE) return 'webp'
  if (mimeType === PNG_MIME_TYPE) return 'png'

  const pathExtension = imageUrl
    .split(/[?#]/)[0]
    ?.match(/\.([a-z0-9]+)$/i)?.[1]
    ?.toLowerCase()

  return pathExtension || 'png'
}

function getMimeTypeFromExtension(imageUrl: string) {
  const pathExtension = imageUrl
    .split(/[?#]/)[0]
    ?.match(/\.([a-z0-9]+)$/i)?.[1]
    ?.toLowerCase()

  if (pathExtension === 'gif') return GIF_MIME_TYPE
  if (pathExtension === 'jpg' || pathExtension === 'jpeg') return JPEG_MIME_TYPE
  if (pathExtension === 'webp') return WEBP_MIME_TYPE
  if (pathExtension === 'png') return PNG_MIME_TYPE

  return null
}

function isSupportedShareImageMimeType(mimeType: string) {
  return (
    mimeType === GIF_MIME_TYPE ||
    mimeType === JPEG_MIME_TYPE ||
    mimeType === PNG_MIME_TYPE ||
    mimeType === WEBP_MIME_TYPE
  )
}

async function inferShareImageMimeType(blob: Blob, imageUrl: string) {
  if (isSupportedShareImageMimeType(blob.type)) return blob.type

  const mimeTypeFromExtension = getMimeTypeFromExtension(imageUrl)
  if (mimeTypeFromExtension) return mimeTypeFromExtension

  const bytes = new Uint8Array(await blob.slice(0, 12).arrayBuffer())
  const isPng =
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47
  const isJpeg = bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff
  const isGif =
    bytes[0] === 0x47 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x38
  const isWebp =
    bytes[0] === 0x52 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x46 &&
    bytes[8] === 0x57 &&
    bytes[9] === 0x45 &&
    bytes[10] === 0x42 &&
    bytes[11] === 0x50

  if (isPng) return PNG_MIME_TYPE
  if (isJpeg) return JPEG_MIME_TYPE
  if (isGif) return GIF_MIME_TYPE
  if (isWebp) return WEBP_MIME_TYPE

  return PNG_MIME_TYPE
}

function isLikelyMobileShareEnvironment() {
  if (typeof navigator === 'undefined') return false

  const userAgent = navigator.userAgent.toLowerCase()
  return (
    /android|iphone|ipad|ipod/.test(userAgent) ||
    (navigator.maxTouchPoints > 1 && /macintosh/.test(userAgent))
  )
}

function isLikelyGifImageUrl(imageUrl: string) {
  return imageUrl.split(/[?#]/)[0]?.toLowerCase().endsWith('.gif') ?? false
}

function isGifShareFile(imageFile: File) {
  return imageFile.type === GIF_MIME_TYPE || imageFile.name.toLowerCase().endsWith('.gif')
}

async function createShareImageFile(imageUrl: string, fileNameBase: string) {
  const response = await fetch(imageUrl)
  if (!response.ok) {
    throw new Error('image-fetch-failed')
  }

  const blob = await response.blob()
  const mimeType = await inferShareImageMimeType(blob, imageUrl)
  const extension = getShareImageExtension(mimeType, imageUrl)

  return new File([blob], `${fileNameBase}.${extension}`, { type: mimeType })
}

async function toClipboardImageBlob(imageFile: File) {
  if (isGifShareFile(imageFile)) {
    throw new Error('gif-clipboard-unavailable')
  }

  if (imageFile.type === 'image/png') return imageFile

  const imageBitmap = await createImageBitmap(imageFile)
  const canvas = document.createElement('canvas')
  canvas.width = imageBitmap.width
  canvas.height = imageBitmap.height

  const context = canvas.getContext('2d')
  if (!context) {
    imageBitmap.close()
    throw new Error('canvas-context-unavailable')
  }

  context.drawImage(imageBitmap, 0, 0)
  imageBitmap.close()

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob)
        return
      }

      reject(new Error('clipboard-image-conversion-failed'))
    }, 'image/png')
  })
}

async function copyShareImage(imageUrl: string, fileNameBase: string) {
  if (!window.navigator.clipboard?.write || typeof ClipboardItem === 'undefined') {
    throw new Error('image-clipboard-unavailable')
  }

  const imageFile = await createShareImageFile(imageUrl, fileNameBase)
  const clipboardImageBlob = await toClipboardImageBlob(imageFile)

  await window.navigator.clipboard.write([
    new ClipboardItem({
      [clipboardImageBlob.type || 'image/png']: clipboardImageBlob,
    }),
  ])
}

async function shareImageFile(title: string, text: string, imageFile: File) {
  if (!navigator.share) {
    throw new Error('file-share-unavailable')
  }

  if (navigator.canShare && !navigator.canShare({ files: [imageFile] })) {
    throw new Error('file-share-unavailable')
  }

  await navigator.share({
    title,
    text,
    files: [imageFile],
  })
}

async function copyShareUrl(text: string) {
  if (window.navigator.clipboard?.writeText) {
    await window.navigator.clipboard.writeText(text)
    return
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()

  try {
    document.execCommand('copy')
  } finally {
    document.body.removeChild(textarea)
  }
}

export async function shareExternalImage({
  title,
  text,
  imageUrl,
  fileNameBase,
  preferNativeFileShare = false,
}: {
  title: string
  text: string
  imageUrl: string
  fileNameBase: string
  preferNativeFileShare?: boolean
}): Promise<ExternalImageShareResult> {
  const shareImageUrl = toAbsoluteShareUrl(imageUrl)
  const isMobileShareEnvironment = isLikelyMobileShareEnvironment()
  const shouldPreferNativeFileShare =
    preferNativeFileShare || isMobileShareEnvironment || isLikelyGifImageUrl(shareImageUrl)

  if (typeof navigator !== 'undefined' && navigator.share && shouldPreferNativeFileShare) {
    try {
      const imageFile = await createShareImageFile(shareImageUrl, fileNameBase)
      if (preferNativeFileShare || isMobileShareEnvironment || isGifShareFile(imageFile)) {
        await shareImageFile(title, text, imageFile)
        return 'native-file'
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return 'cancelled'
      }
    }

    if (!preferNativeFileShare && isMobileShareEnvironment) {
      await navigator.share({
        title,
        text,
        url: shareImageUrl,
      })
      return 'native-url'
    }
  }

  if (isLikelyGifImageUrl(shareImageUrl)) {
    await copyShareUrl(shareImageUrl)
    return 'copied-gif-link'
  }

  try {
    await copyShareImage(shareImageUrl, fileNameBase)
    return 'copied-image'
  } catch {
    // Some browsers block binary image clipboard writes, so fall back to URL copy.
  }

  await copyShareUrl(shareImageUrl)
  return 'copied-image-link'
}
