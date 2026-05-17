'use client'

import { useCallback, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'

import { ApiError, postShare } from '@/shared/apis'
import type { FlipbookResultItemResponse, ShareCreateResponse } from '@/shared/types'
import { getDisplayImageUrl, writeCommunityCanvasHandoffDraft } from '@/shared/utils'

function hasUsableImageUrl(imageUrl: string | null | undefined) {
  return Boolean(imageUrl?.trim())
}

function selectFirstImageUrl(...imageUrls: Array<string | null | undefined>) {
  return imageUrls.find(hasUsableImageUrl) ?? null
}

function getResultOwnerName({
  activeResult,
  activeResultIndex,
  resultOwnerNames,
}: {
  activeResult: FlipbookResultItemResponse | null
  activeResultIndex: number
  resultOwnerNames: string[]
}) {
  const flipbookIndex = activeResult?.flipbookIndex ?? activeResultIndex

  return (
    resultOwnerNames[flipbookIndex] ??
    activeResult?.frames.find((frame) => frame.frameIndex === 0)?.drawnByNickname ??
    activeResult?.frames[0]?.drawnByNickname ??
    `작품 ${flipbookIndex + 1}`
  )
}

function getResultImageUrl(activeResult: FlipbookResultItemResponse | null) {
  if (!activeResult) return null

  return getDisplayImageUrl(
    selectFirstImageUrl(
      activeResult.gifUrl,
      activeResult.firstImageUrl,
      activeResult.thumbnailUrl,
      ...activeResult.frames.map((frame) => frame.imageUrl),
    ),
  )
}

function getCommunityImageUrl(activeResult: FlipbookResultItemResponse | null) {
  if (!activeResult) return null

  return getDisplayImageUrl(
    selectFirstImageUrl(
      activeResult.firstImageUrl,
      activeResult.thumbnailUrl,
      ...activeResult.frames.map((frame) => frame.imageUrl),
      activeResult.gifUrl,
    ),
  )
}

function getResultFileExtension(blobType: string, imageUrl: string) {
  if (blobType.includes('gif') || imageUrl.toLowerCase().includes('.gif')) return 'gif'
  if (blobType.includes('svg') || imageUrl.startsWith('data:image/svg+xml')) return 'svg'
  if (blobType.includes('webp') || imageUrl.toLowerCase().includes('.webp')) return 'webp'
  if (blobType.includes('jpeg') || imageUrl.toLowerCase().match(/\.jpe?g($|\?)/)) return 'jpg'

  return 'png'
}

function toSafeFileName(fileName: string, extension: string) {
  const safeName = fileName.trim().replace(/[\\/:*?"<>|]+/g, '-')
  const normalizedName = safeName.length > 0 ? safeName : 'flipbook'

  return normalizedName.toLowerCase().endsWith(`.${extension}`)
    ? normalizedName
    : `${normalizedName}.${extension}`
}

function isRealGalleryId(galleryId: string | null | undefined) {
  return Boolean(galleryId && !galleryId.startsWith('dummy-'))
}

function hasUsableShareUrl(shareUrl: string | null | undefined) {
  return Boolean(shareUrl?.trim())
}

function toExternalShareErrorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message || '외부 공유 정보를 만들 수 없어요.'
  if (error instanceof Error && error.message.includes('클립보드')) {
    return '외부 공유 링크를 복사하지 못했어요.'
  }

  return '외부 공유 정보를 만들 수 없어요.'
}

function getDefaultShareUrl(shareInfo: ShareCreateResponse) {
  return (
    [shareInfo.siteUrl, shareInfo.kakaoUrl, shareInfo.instagramUrl]
      .find(hasUsableShareUrl) ?? null
  )
}

function toAbsoluteShareUrl(shareUrl: string) {
  if (typeof window === 'undefined') return shareUrl

  return new URL(shareUrl, window.location.origin).toString()
}

function openExternalShareUrl(shareUrl: string) {
  const openedWindow = window.open(
    toAbsoluteShareUrl(shareUrl),
    '_blank',
    'noopener,noreferrer',
  )

  if (!openedWindow) {
    window.location.assign(toAbsoluteShareUrl(shareUrl))
  }
}

async function copyTextWithFallback(text: string) {
  if (window.navigator.clipboard?.writeText) {
    try {
      await window.navigator.clipboard.writeText(text)
      return
    } catch {
      // Clipboard API 차단 시 DOM fallback으로 이어간다.
    }
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '-9999px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.focus()
  textarea.select()
  textarea.setSelectionRange(0, text.length)
  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)

  if (!copied) {
    throw new Error('클립보드 복사에 실패했습니다.')
  }
}

export function useFlipbookResultActions({
  activeResult,
  activeResultIndex,
  resultOwnerNames,
  onReturnToLobby,
}: {
  activeResult: FlipbookResultItemResponse | null
  activeResultIndex: number
  resultOwnerNames: string[]
  onReturnToLobby: () => void
}) {
  const router = useRouter()
  const [isSavingToLocal, setIsSavingToLocal] = useState(false)
  const [isSharingExternal, setIsSharingExternal] = useState(false)
  const [externalShareState, setExternalShareState] = useState<{
    galleryId: string
    shareInfo: ShareCreateResponse
  } | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const ownerName = useMemo(
    () =>
      getResultOwnerName({
        activeResult,
        activeResultIndex,
        resultOwnerNames,
      }),
    [activeResult, activeResultIndex, resultOwnerNames],
  )
  const resultImageUrl = useMemo(() => getResultImageUrl(activeResult), [activeResult])
  const communityImageUrl = useMemo(() => getCommunityImageUrl(activeResult), [activeResult])
  const canSaveToLocal = Boolean(resultImageUrl) && !isSavingToLocal
  const canPostCommunity = Boolean(communityImageUrl)
  const canShareExternal =
    Boolean(activeResult && isRealGalleryId(activeResult.galleryId)) && !isSharingExternal
  const externalShareInfo =
    externalShareState !== null && activeResult?.galleryId === externalShareState.galleryId
      ? externalShareState.shareInfo
      : null

  const saveToLocalGallery = useCallback(async () => {
    if (!resultImageUrl || isSavingToLocal) return

    setIsSavingToLocal(true)
    setActionMessage(null)

    try {
      const response = await fetch(resultImageUrl)
      if (!response.ok) {
        throw new Error('flipbook-result-download-failed')
      }

      const resultBlob = await response.blob()
      const objectUrl = window.URL.createObjectURL(resultBlob)
      const downloadLink = document.createElement('a')
      const fileExtension = getResultFileExtension(resultBlob.type, resultImageUrl)

      downloadLink.href = objectUrl
      downloadLink.download = toSafeFileName(`flipbook-${ownerName}`, fileExtension)
      downloadLink.rel = 'noopener noreferrer'
      document.body.append(downloadLink)
      downloadLink.click()
      downloadLink.remove()

      window.setTimeout(() => {
        window.URL.revokeObjectURL(objectUrl)
      }, 1000)
      setActionMessage('로컬 보관함에 저장했어요.')
    } catch {
      setActionMessage('로컬 저장에 실패했어요. 잠시 후 다시 시도해주세요.')
    } finally {
      setIsSavingToLocal(false)
    }
  }, [isSavingToLocal, ownerName, resultImageUrl])

  const postToCommunity = useCallback(() => {
    if (!activeResult || !communityImageUrl) {
      setActionMessage('커뮤니티에 게시할 이미지를 찾지 못했어요.')
      return
    }

    writeCommunityCanvasHandoffDraft({
      sourceKind: 'FLIPBOOK',
      title: `${ownerName}의 플립북`,
      imageUrl: communityImageUrl,
      thumbnailUrl: activeResult.thumbnailUrl ?? communityImageUrl,
      sourceGalleryId: isRealGalleryId(activeResult.galleryId) ? activeResult.galleryId : null,
      sourceContentKind: 'flipbook',
    })
    router.push('/community-canvas')
  }, [activeResult, communityImageUrl, ownerName, router])

  const shareExternal = useCallback(async () => {
    if (!activeResult || !isRealGalleryId(activeResult.galleryId) || isSharingExternal) {
      setActionMessage('외부 공유는 저장된 결과에서만 사용할 수 있어요.')
      return
    }

    setIsSharingExternal(true)
    setActionMessage(null)

    try {
      const shareInfo = await postShare({
        galleryId: activeResult.galleryId,
        campaign: 'flipbook_result',
      })
      if (!getDefaultShareUrl(shareInfo)) {
        throw new Error('share-url-missing')
      }

      setExternalShareState({
        galleryId: activeResult.galleryId,
        shareInfo,
      })
    } catch (error) {
      setActionMessage(toExternalShareErrorMessage(error))
    } finally {
      setIsSharingExternal(false)
    }
  }, [activeResult, isSharingExternal])

  const closeExternalShare = useCallback(() => {
    setExternalShareState(null)
  }, [])

  const openKakaoExternalShare = useCallback(() => {
    if (!externalShareInfo || !hasUsableShareUrl(externalShareInfo.kakaoUrl)) {
      setActionMessage('카카오톡 공유 링크를 찾지 못했어요.')
      return
    }

    openExternalShareUrl(externalShareInfo.kakaoUrl)
    setActionMessage('카카오톡 공유를 열었어요.')
  }, [externalShareInfo])

  const openInstagramExternalShare = useCallback(() => {
    if (!externalShareInfo || !hasUsableShareUrl(externalShareInfo.instagramUrl)) {
      setActionMessage('인스타그램 공유 링크를 찾지 못했어요.')
      return
    }

    openExternalShareUrl(externalShareInfo.instagramUrl)
    setActionMessage('인스타그램 공유를 열었어요.')
  }, [externalShareInfo])

  const copyExternalShareLink = useCallback(async () => {
    if (!externalShareInfo) return

    const shareUrl = getDefaultShareUrl(externalShareInfo)
    if (!shareUrl) {
      setActionMessage('외부 공유 링크를 찾지 못했어요.')
      return
    }

    try {
      await copyTextWithFallback(toAbsoluteShareUrl(shareUrl))
      setActionMessage('외부 공유 링크를 복사했어요.')
    } catch (error) {
      setActionMessage(toExternalShareErrorMessage(error))
    }
  }, [externalShareInfo])

  return {
    actionMessage,
    canPostCommunity,
    canSaveToLocal,
    canShareExternal,
    closeExternalShare,
    copyExternalShareLink,
    externalShareInfo,
    isSavingToLocal,
    isSharingExternal,
    openInstagramExternalShare,
    openKakaoExternalShare,
    postToCommunity,
    saveToLocalGallery,
    shareExternal,
    returnToLobby: onReturnToLobby,
  }
}
