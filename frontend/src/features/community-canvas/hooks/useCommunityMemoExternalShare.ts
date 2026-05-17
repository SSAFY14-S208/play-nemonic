'use client'

import { useCallback, useState } from 'react'
import { toast } from 'sonner'
import { ApiError, postCommunityMemoShare } from '@/shared/apis'
import type { CommunityMemoDetailResponse } from '@/shared/types'

const GIF_MIME_TYPE = 'image/gif'
const DEFAULT_SHARE_ERROR_MESSAGE =
  '\uacf5\uc720 \uc815\ubcf4\ub97c \ub9cc\ub4e4\uc9c0 \ubabb\ud588\uc5b4\uc694.'
const DEFAULT_AUTHOR_NICKNAME = '\ucee4\ubba4\ub2c8\ud2f0'
const SHARE_TEXT =
  '\ub124\ubaa8\ub2c9 \ucee4\ubba4\ub2c8\ud2f0 \uce94\ubc84\uc2a4 \uba54\ubaa8\ub97c \uacf5\uc720\ud574\uc694.'
const GIF_LINK_COPIED_MESSAGE =
  'QR GIF \uacf5\uc720 \ub9c1\ud06c\ub97c \ubcf5\uc0ac\ud588\uc5b4\uc694.'
const IMAGE_COPIED_MESSAGE =
  'QR \uacf5\uc720 \uc774\ubbf8\uc9c0\ub97c \ubcf5\uc0ac\ud588\uc5b4\uc694. \ucc44\ud305\ucc3d\uc5d0 \ubd99\uc5ec\ub123\uc5b4 \uc8fc\uc138\uc694.'
const IMAGE_LINK_COPIED_MESSAGE =
  'QR \uacf5\uc720 \uc774\ubbf8\uc9c0 \ub9c1\ud06c\ub97c \ubcf5\uc0ac\ud588\uc5b4\uc694.'

function toAbsoluteShareUrl(imageUrl: string) {
  if (typeof window === 'undefined') return imageUrl
  return new URL(imageUrl, window.location.origin).toString()
}

function getShareImageExtension(mimeType: string, imageUrl: string) {
  if (mimeType === GIF_MIME_TYPE) return 'gif'
  if (mimeType === 'image/jpeg') return 'jpg'
  if (mimeType === 'image/webp') return 'webp'
  if (mimeType === 'image/png') return 'png'

  const pathExtension = imageUrl
    .split(/[?#]/)[0]
    ?.match(/\.([a-z0-9]+)$/i)?.[1]
    ?.toLowerCase()

  return pathExtension || 'png'
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

async function createShareImageFile(imageUrl: string) {
  const response = await fetch(imageUrl)
  if (!response.ok) {
    throw new Error('image-fetch-failed')
  }

  const blob = await response.blob()
  const mimeType = blob.type || 'image/jpeg'
  const extension = getShareImageExtension(mimeType, imageUrl)

  return new File([blob], `community-memo-qr.${extension}`, { type: mimeType })
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

async function copyShareImage(imageUrl: string) {
  if (!window.navigator.clipboard?.write || typeof ClipboardItem === 'undefined') {
    throw new Error('image-clipboard-unavailable')
  }

  const imageFile = await createShareImageFile(imageUrl)
  const clipboardImageBlob = await toClipboardImageBlob(imageFile)

  await window.navigator.clipboard.write([
    new ClipboardItem({
      [clipboardImageBlob.type || 'image/png']: clipboardImageBlob,
    }),
  ])
}

async function shareImageFile(title: string, text: string, imageFile: File) {
  if (!navigator.share || !navigator.canShare?.({ files: [imageFile] })) {
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

function toErrorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message || DEFAULT_SHARE_ERROR_MESSAGE
  if (error instanceof Error) return error.message || DEFAULT_SHARE_ERROR_MESSAGE
  return DEFAULT_SHARE_ERROR_MESSAGE
}

export function useCommunityMemoExternalShare() {
  const [isSharing, setIsSharing] = useState(false)

  const shareCommunityMemo = useCallback(async (detail: CommunityMemoDetailResponse | null) => {
    if (!detail || isSharing) return

    setIsSharing(true)
    try {
      const shareInfo = await postCommunityMemoShare(detail.memoUuid)
      const shareImageUrl = toAbsoluteShareUrl(shareInfo.imageUrl)
      const title = `${detail.authorNickname || DEFAULT_AUTHOR_NICKNAME}\uc758 \uba54\ubaa8`
      const isMobileShareEnvironment = isLikelyMobileShareEnvironment()
      const shouldPreferNativeFileShare =
        isMobileShareEnvironment || isLikelyGifImageUrl(shareImageUrl)

      if (navigator.share && shouldPreferNativeFileShare) {
        try {
          const imageFile = await createShareImageFile(shareImageUrl)
          if (isMobileShareEnvironment || isGifShareFile(imageFile)) {
            await shareImageFile(title, SHARE_TEXT, imageFile)
            return
          }
        } catch (error) {
          if (error instanceof DOMException && error.name === 'AbortError') {
            return
          }
        }

        if (isMobileShareEnvironment) {
          await navigator.share({
            title,
            text: SHARE_TEXT,
            url: shareImageUrl,
          })
          return
        }
      }

      if (isLikelyGifImageUrl(shareImageUrl)) {
        await copyShareUrl(shareImageUrl)
        toast.success(GIF_LINK_COPIED_MESSAGE)
        return
      }

      try {
        await copyShareImage(shareImageUrl)
        toast.success(IMAGE_COPIED_MESSAGE)
        return
      } catch {
        // Some browsers block binary image clipboard writes, so fall back to URL copy.
      }

      await copyShareUrl(shareImageUrl)
      toast.success(IMAGE_LINK_COPIED_MESSAGE)
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return

      toast.error(toErrorMessage(error))
    } finally {
      setIsSharing(false)
    }
  }, [isSharing])

  return {
    isSharing,
    shareCommunityMemo,
  }
}
