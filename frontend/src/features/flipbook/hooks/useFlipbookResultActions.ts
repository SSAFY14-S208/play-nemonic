'use client'

import { useCallback, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'

import { ApiError, postArtifactShare } from '@/shared/apis'
import type { FlipbookResultItemResponse } from '@/shared/types'
import {
  getDisplayImageUrl,
  shareExternalImage,
  type ExternalImageShareResult,
  writeCommunityCanvasHandoffDraft,
} from '@/shared/utils'

const FLIPBOOK_SHARE_TEXT = '네모닉 플립북 결과를 공유해요.'
const FLIPBOOK_SHARE_GIF_LINK_COPIED_MESSAGE =
  '플립북 QR GIF 공유 링크를 복사했어요.'
const FLIPBOOK_SHARE_IMAGE_COPIED_MESSAGE =
  '플립북 QR 공유 이미지를 복사했어요. 채팅창에 붙여넣어 주세요.'
const FLIPBOOK_SHARE_IMAGE_LINK_COPIED_MESSAGE =
  '플립북 QR 공유 이미지 링크를 복사했어요.'

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

function isRealResourceId(resourceId: string | null | undefined) {
  return Boolean(resourceId && !resourceId.startsWith('dummy-'))
}

function toExternalShareErrorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message || '외부 공유 정보를 만들 수 없어요.'
  if (error instanceof Error) return error.message || '외부 공유 정보를 만들 수 없어요.'

  return '외부 공유 정보를 만들 수 없어요.'
}

function getExternalShareSuccessMessage(shareResult: ExternalImageShareResult) {
  if (shareResult === 'copied-gif-link') return FLIPBOOK_SHARE_GIF_LINK_COPIED_MESSAGE
  if (shareResult === 'copied-image') return FLIPBOOK_SHARE_IMAGE_COPIED_MESSAGE
  if (shareResult === 'copied-image-link') return FLIPBOOK_SHARE_IMAGE_LINK_COPIED_MESSAGE

  return null
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
    Boolean(activeResult && isRealResourceId(activeResult.artifactId)) && !isSharingExternal

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
      sourceGalleryId: isRealResourceId(activeResult.galleryId) ? activeResult.galleryId : null,
      sourceContentKind: 'flipbook',
    })
    router.push('/community-canvas')
  }, [activeResult, communityImageUrl, ownerName, router])

  const shareExternal = useCallback(async () => {
    if (!activeResult || !isRealResourceId(activeResult.artifactId) || isSharingExternal) {
      setActionMessage('외부 공유는 저장된 결과에서만 사용할 수 있어요.')
      return
    }

    setIsSharingExternal(true)
    setActionMessage(null)

    try {
      const shareInfo = await postArtifactShare(activeResult.artifactId)
      if (!shareInfo.imageUrl?.trim()) {
        throw new Error('외부 공유 이미지를 찾지 못했어요.')
      }

      const shareResult = await shareExternalImage({
        title: `${ownerName}의 플립북`,
        text: FLIPBOOK_SHARE_TEXT,
        imageUrl: shareInfo.imageUrl,
        fileNameBase: 'flipbook-result-qr',
      })
      const successMessage = getExternalShareSuccessMessage(shareResult)
      if (successMessage) toast.success(successMessage)
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return

      toast.error(toExternalShareErrorMessage(error))
    } finally {
      setIsSharingExternal(false)
    }
  }, [activeResult, isSharingExternal, ownerName])

  return {
    actionMessage,
    canPostCommunity,
    canSaveToLocal,
    canShareExternal,
    isSavingToLocal,
    isSharingExternal,
    postToCommunity,
    saveToLocalGallery,
    shareExternal,
    returnToLobby: onReturnToLobby,
  }
}
