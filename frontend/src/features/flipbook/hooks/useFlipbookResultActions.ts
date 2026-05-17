'use client'

import { useCallback, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'

import type { FlipbookResultItemResponse } from '@/shared/types'
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

  return {
    actionMessage,
    canPostCommunity,
    canSaveToLocal,
    isSavingToLocal,
    postToCommunity,
    saveToLocalGallery,
    returnToLobby: onReturnToLobby,
  }
}
