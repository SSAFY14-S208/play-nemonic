'use client'

import { useCallback, useEffect, useState } from 'react'
import { HTTPError } from 'ky'
import { toast } from 'sonner'
import {
  ApiError,
  getGallery,
  getGalleryList,
  postCommunityMemo,
  postFileConfirm,
  postFilePresign,
} from '@/shared/apis'
import { DRAWING_COLORS, DEFAULT_DRAWING_STROKE_WIDTH } from '@/shared/constants'
import { useDrawingBoard } from '@/shared/hooks/useDrawingBoard'
import type {
  CommunityMemoCreateRequest,
  CommunityMemoDetailResponse,
  GalleryDetailResponse,
  GalleryItemResponse,
  MemoSourceType,
} from '@/shared/types'
import {
  playBrowserAudio,
  preloadBrowserAudio,
  type CommunityCanvasHandoffDraft,
} from '@/shared/utils'
import {
  DEFAULT_COMMUNITY_MEMO_COLOR,
  COMMUNITY_SNAPSHOT_CONTENT_TYPE,
  exportDirectCommunitySnapshot,
  exportGalleryCommunitySnapshot,
  hasDirectSnapshotContent,
  playCommunityMemoAttachSound,
} from '../utils'
import type { CommunityMemoLayoutDraft } from './useCommunityCanvas'

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'

export const COMMUNITY_COMPOSER_BOARD_SIZE = {
  width: 720,
  height: 540,
}

const COMMUNITY_DRAWING_BACKGROUND = '#fbfaff'
const GALLERY_PAGE_SIZE = 12
const COMMUNITY_MEMO_PRINT_START_SOUND_PATH = '/sounds/print_label.mp3'
const COMMUNITY_MEMO_PRINT_START_SOUND_VOLUME = 0.36
const COMMUNITY_MEMO_PRINT_START_SOUND_OFFSET_SECONDS = 0.2
const MODERATION_BLOCKED_TOAST_MESSAGE =
  '부적절한 내용이 감지되어 메모 게시를 취소했어요.'

interface UseCommunityComposerOptions {
  onCreated: (createdMemo: CommunityMemoDetailResponse) => Promise<void>
}

export interface CommunityPendingMemoPlacement {
  sourceType: MemoSourceType
  originalBlob: Blob
  thumbnailBlob: Blob
  previewUrl: string
  memoColor: string
  sourceGalleryId?: string | null
  decoration?: unknown
}

function toErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) return error.message || fallback
  if (error instanceof HTTPError) return fallback
  if (error instanceof Error) return error.message || fallback
  return fallback
}

function includesModerationBlockedMessage(value: unknown): boolean {
  if (typeof value === 'string') {
    return (
      value.includes('부적절') ||
      value.includes('모더레이션') ||
      value.toLowerCase().includes('moderation')
    )
  }

  if (Array.isArray(value)) {
    return value.some(includesModerationBlockedMessage)
  }

  if (value && typeof value === 'object') {
    return Object.values(value).some(includesModerationBlockedMessage)
  }

  return false
}

function isGifImageUrl(imageUrl: string | null | undefined) {
  if (!imageUrl) return false
  return /\.gif(?:[?#].*)?$/i.test(imageUrl)
}

function getGalleryPlaybackImageUrl({
  imageUrl,
  sourceContentKind,
}: {
  imageUrl: string
  sourceContentKind: string | null | undefined
}) {
  const normalizedContentKind = sourceContentKind?.toLowerCase()
  if (isGifImageUrl(imageUrl) || normalizedContentKind === 'flipbook') {
    return imageUrl
  }

  return null
}

async function isCommunityMemoModerationBlockedError(error: unknown) {
  if (error instanceof ApiError) {
    return includesModerationBlockedMessage(error.message)
  }

  if (!(error instanceof HTTPError)) return false
  if (error.response.status !== 400) return false
  if (!error.response.url.includes('/community/memos')) return false

  try {
    const body = (await error.response.clone().json()) as unknown
    if (includesModerationBlockedMessage(body)) return true
  } catch {
    // A moderation rejection can arrive as an empty/plain 400 response depending on
    // the gateway, so the create endpoint still gets the moderation toast.
  }

  return true
}

async function uploadCommunityImage(blob: Blob, fileName: string) {
  const presign = await postFilePresign({
    fileName,
    contentType: COMMUNITY_SNAPSHOT_CONTENT_TYPE,
    purpose: 'COMMUNITY',
    byteSize: blob.size,
  })

  const response = await fetch(presign.presignedUrl, {
    method: 'PUT',
    body: blob,
    headers: {
      'Content-Type': COMMUNITY_SNAPSHOT_CONTENT_TYPE,
    },
  })

  if (!response.ok) {
    throw new Error(`community-upload-failed-${response.status}`)
  }

  await postFileConfirm(presign.fileId)
  return presign.fileId
}

export function useCommunityComposer({ onCreated }: UseCommunityComposerOptions) {
  const drawingBoard = useDrawingBoard({
    boardSize: COMMUNITY_COMPOSER_BOARD_SIZE,
    backgroundColor: COMMUNITY_DRAWING_BACKGROUND,
    defaultColor: DRAWING_COLORS[0],
    defaultStrokeWidth: DEFAULT_DRAWING_STROKE_WIDTH,
  })

  const [isComposerOpen, setComposerOpen] = useState(false)
  const [sourceType, setSourceType] = useState<MemoSourceType>('DIRECT')
  const [pendingPlacement, setPendingPlacement] =
    useState<CommunityPendingMemoPlacement | null>(null)
  const [printRevealPlacement, setPrintRevealPlacement] =
    useState<CommunityPendingMemoPlacement | null>(null)
  const [galleryItems, setGalleryItems] = useState<GalleryItemResponse[]>([])
  const [galleryStatus, setGalleryStatus] = useState<AsyncStatus>('idle')
  const [galleryError, setGalleryError] = useState<string | null>(null)
  const [selectedGalleryId, setSelectedGalleryId] = useState<string | null>(null)
  const [selectedGalleryDetail, setSelectedGalleryDetail] =
    useState<GalleryDetailResponse | null>(null)
  const [handoffDraft, setHandoffDraft] =
    useState<CommunityCanvasHandoffDraft | null>(null)
  const [galleryDetailStatus, setGalleryDetailStatus] = useState<AsyncStatus>('idle')
  const [postStatus, setPostStatus] = useState<AsyncStatus>('idle')
  const [selectedMemoColor, setSelectedMemoColor] = useState(DEFAULT_COMMUNITY_MEMO_COLOR)

  useEffect(
    () => () => {
      if (pendingPlacement) {
        URL.revokeObjectURL(pendingPlacement.previewUrl)
      }
    },
    [pendingPlacement],
  )

  useEffect(
    () => () => {
      if (printRevealPlacement) {
        URL.revokeObjectURL(printRevealPlacement.previewUrl)
      }
    },
    [printRevealPlacement],
  )

  const loadGalleryItems = useCallback(async () => {
    setGalleryStatus('loading')
    setGalleryError(null)

    try {
      const response = await getGalleryList({ page: 0, size: GALLERY_PAGE_SIZE })
      setGalleryItems(response.items)
      setGalleryStatus('success')
    } catch (error) {
      setGalleryError(toErrorMessage(error, '갤러리 목록을 불러오지 못했어요.'))
      setGalleryStatus('error')
    }
  }, [])

  const openComposer = useCallback(
    () => {
      setSourceType('DIRECT')
      setSelectedGalleryId(null)
      setSelectedGalleryDetail(null)
      setHandoffDraft(null)
      setGalleryDetailStatus('idle')
      setPostStatus('idle')
      setSelectedMemoColor(DEFAULT_COMMUNITY_MEMO_COLOR)
      setPendingPlacement(null)
      setPrintRevealPlacement(null)
      preloadBrowserAudio(
        COMMUNITY_MEMO_PRINT_START_SOUND_PATH,
        COMMUNITY_MEMO_PRINT_START_SOUND_VOLUME,
      )
      drawingBoard.replaceLines([])
      setComposerOpen(true)
    },
    [drawingBoard],
  )

  const openComposerWithHandoffDraft = useCallback(
    (draft: CommunityCanvasHandoffDraft) => {
      setSourceType('GALLERY')
      setSelectedGalleryId(draft.sourceGalleryId ?? null)
      setSelectedGalleryDetail(null)
      setHandoffDraft(draft)
      setGalleryDetailStatus('idle')
      setPostStatus('idle')
      setSelectedMemoColor(DEFAULT_COMMUNITY_MEMO_COLOR)
      setPendingPlacement(null)
      setPrintRevealPlacement(null)
      preloadBrowserAudio(
        COMMUNITY_MEMO_PRINT_START_SOUND_PATH,
        COMMUNITY_MEMO_PRINT_START_SOUND_VOLUME,
      )
      drawingBoard.replaceLines([])
      if (drawingBoard.selectedToolKey === 'bucket') {
        drawingBoard.setSelectedToolKey('pencil')
      }
      setComposerOpen(true)
    },
    [drawingBoard],
  )

  const closeComposer = useCallback(() => {
    setComposerOpen(false)
    setPostStatus('idle')
  }, [])

  const selectSourceType = useCallback(
    (nextSourceType: MemoSourceType) => {
      setSourceType(nextSourceType)
      drawingBoard.replaceLines([])
      if (nextSourceType === 'DIRECT') {
        setHandoffDraft(null)
        setSelectedGalleryId(null)
        setSelectedGalleryDetail(null)
        setGalleryDetailStatus('idle')
      }
      if (nextSourceType === 'GALLERY' && drawingBoard.selectedToolKey === 'bucket') {
        drawingBoard.setSelectedToolKey('pencil')
      }
      if (nextSourceType === 'GALLERY' && galleryStatus === 'idle') {
        void loadGalleryItems()
      }
    },
    [drawingBoard, galleryStatus, loadGalleryItems],
  )

  const selectGalleryItem = useCallback(async (galleryId: string) => {
    setSelectedGalleryId(galleryId)
    setSelectedGalleryDetail(null)
    setHandoffDraft(null)
    setGalleryDetailStatus('loading')
    drawingBoard.replaceLines([])

    try {
      const detail = await getGallery(galleryId)
      setSelectedGalleryDetail(detail)
      setGalleryDetailStatus('success')
    } catch (error) {
      setGalleryDetailStatus('error')
      toast.error(toErrorMessage(error, '갤러리 항목을 불러오지 못했어요.'))
    }
  }, [drawingBoard])

  const prepareDirectMemoPlacement = useCallback(async () => {
    if (!hasDirectSnapshotContent(drawingBoard.lines)) {
      toast.error('그림을 먼저 남겨주세요.')
      return
    }

    setPostStatus('loading')
    try {
      const snapshot = await exportDirectCommunitySnapshot({
        boardSize: COMMUNITY_COMPOSER_BOARD_SIZE,
        lines: drawingBoard.lines,
        backgroundColor: COMMUNITY_DRAWING_BACKGROUND,
      })

      playBrowserAudio(
        COMMUNITY_MEMO_PRINT_START_SOUND_PATH,
        COMMUNITY_MEMO_PRINT_START_SOUND_VOLUME,
        COMMUNITY_MEMO_PRINT_START_SOUND_OFFSET_SECONDS,
      )
      setPrintRevealPlacement({
        sourceType: 'DIRECT',
        originalBlob: snapshot.originalBlob,
        thumbnailBlob: snapshot.thumbnailBlob,
        previewUrl: URL.createObjectURL(snapshot.thumbnailBlob),
        memoColor: selectedMemoColor,
        decoration: {
          kind: 'community-direct-v1',
          memoColor: selectedMemoColor,
          lines: drawingBoard.lines,
        },
      })
      setComposerOpen(false)
      setPostStatus('success')
    } catch (error) {
      setPostStatus('error')
      toast.error(toErrorMessage(error, '메모지 준비에 실패했어요.'))
    }
  }, [drawingBoard.lines, selectedMemoColor])

  const prepareGalleryMemoPlacement = useCallback(async () => {
    const imageUrl =
      handoffDraft?.imageUrl ||
      selectedGalleryDetail?.contentUrl ||
      selectedGalleryDetail?.thumbnailUrl
    const sourceGalleryId = handoffDraft?.sourceGalleryId ?? selectedGalleryId
    const sourceContentKind =
      handoffDraft?.sourceContentKind ?? selectedGalleryDetail?.kind ?? null

    if (!handoffDraft && (!selectedGalleryId || !selectedGalleryDetail)) {
      toast.error('붙일 갤러리 항목을 선택해주세요.')
      return
    }

    if (!imageUrl) {
      toast.error('선택한 항목의 이미지를 찾지 못했어요.')
      return
    }

    setPostStatus('loading')
    try {
      const snapshot = await exportGalleryCommunitySnapshot({
        imageUrl,
        boardSize: COMMUNITY_COMPOSER_BOARD_SIZE,
        lines: drawingBoard.lines,
        backgroundColor: COMMUNITY_DRAWING_BACKGROUND,
      })

      playBrowserAudio(
        COMMUNITY_MEMO_PRINT_START_SOUND_PATH,
        COMMUNITY_MEMO_PRINT_START_SOUND_VOLUME,
        COMMUNITY_MEMO_PRINT_START_SOUND_OFFSET_SECONDS,
      )
      setPrintRevealPlacement({
        sourceType: sourceGalleryId ? 'GALLERY' : 'DIRECT',
        originalBlob: snapshot.originalBlob,
        thumbnailBlob: snapshot.thumbnailBlob,
        previewUrl: URL.createObjectURL(snapshot.thumbnailBlob),
        memoColor: selectedMemoColor,
        sourceGalleryId,
        decoration: {
          kind: handoffDraft ? 'community-handoff-v1' : 'community-gallery-v1',
          memoColor: selectedMemoColor,
          sourceGalleryId: sourceGalleryId ?? null,
          sourceKind: handoffDraft?.sourceKind ?? 'GALLERY',
          sourceTitle: handoffDraft?.title ?? null,
          sourceContentKind,
          sourcePlaybackImageUrl: getGalleryPlaybackImageUrl({
            imageUrl,
            sourceContentKind,
          }),
          lines: drawingBoard.lines,
        },
      })
      setComposerOpen(false)
      setPostStatus('success')
    } catch (error) {
      setPostStatus('error')
      toast.error(toErrorMessage(error, '갤러리 메모지 준비에 실패했어요.'))
    }
  }, [
    drawingBoard.lines,
    handoffDraft,
    selectedGalleryDetail,
    selectedGalleryId,
    selectedMemoColor,
  ])

  const attachPendingMemo = useCallback(
    async (layout: CommunityMemoLayoutDraft) => {
      if (!pendingPlacement) return

      setPostStatus('loading')
      try {
        const createdAt = Date.now()
        const filePrefix =
          pendingPlacement.sourceType === 'DIRECT' ? 'community-direct' : 'community-gallery'
        const originalFileId = await uploadCommunityImage(
          pendingPlacement.originalBlob,
          `${filePrefix}-${createdAt}.png`,
        )
        const thumbnailFileId = await uploadCommunityImage(
          pendingPlacement.thumbnailBlob,
          `${filePrefix}-${createdAt}-thumbnail.png`,
        )

        const payload: CommunityMemoCreateRequest = {
          sourceType: pendingPlacement.sourceType,
          sourceGalleryId: pendingPlacement.sourceGalleryId,
          originalFileId,
          thumbnailFileId,
          positionX: layout.positionX,
          positionY: layout.positionY,
          zIndex: layout.zIndex,
          rotationDeg: layout.rotationDeg,
          decoration: pendingPlacement.decoration,
        }

        const createdMemo = await postCommunityMemo(payload)
        await onCreated(createdMemo)
        playCommunityMemoAttachSound()
        setPendingPlacement(null)
        setPostStatus('success')
        toast.success('커뮤니티 벽에 메모를 붙였어요.')
      } catch (error) {
        if (await isCommunityMemoModerationBlockedError(error)) {
          setPendingPlacement(null)
          setPostStatus('idle')
          toast.error(MODERATION_BLOCKED_TOAST_MESSAGE)
          return
        }

        setPostStatus('error')
        toast.error(toErrorMessage(error, '메모 게시에 실패했어요.'))
      }
    },
    [onCreated, pendingPlacement],
  )

  const acceptPrintedMemoPlacement = useCallback(() => {
    if (!printRevealPlacement) return

    setPendingPlacement({
      ...printRevealPlacement,
      previewUrl: URL.createObjectURL(printRevealPlacement.thumbnailBlob),
    })
    setPrintRevealPlacement(null)
    setPostStatus('success')
    toast.success('벽에서 붙일 자리를 골라주세요.')
  }, [printRevealPlacement])

  const cancelPrintedMemoPlacement = useCallback(() => {
    setPrintRevealPlacement(null)
    setPostStatus('idle')
  }, [])

  const cancelPendingPlacement = useCallback(() => {
    setPendingPlacement(null)
    setPostStatus('idle')
  }, [])

  return {
    isComposerOpen,
    sourceType,
    pendingPlacement,
    printRevealPlacement,
    drawingBoard,
    galleryItems,
    galleryStatus,
    galleryError,
    selectedGalleryId,
    selectedGalleryDetail,
    handoffDraft,
    galleryDetailStatus,
    postStatus,
    selectedMemoColor,
    backgroundColor: COMMUNITY_DRAWING_BACKGROUND,
    openComposer,
    openComposerWithHandoffDraft,
    closeComposer,
    selectSourceType,
    loadGalleryItems,
    selectGalleryItem,
    setSelectedMemoColor,
    prepareDirectMemoPlacement,
    prepareGalleryMemoPlacement,
    acceptPrintedMemoPlacement,
    cancelPrintedMemoPlacement,
    attachPendingMemo,
    cancelPendingPlacement,
  }
}
