import { useEffect, useMemo, useState } from 'react'
import { getCommunityMemoList } from '@/shared/apis'
import { COMMUNITY_CANVAS_ATTACHABLE_SURFACE_BOUNDS } from '@/shared/constants'
import type { CommunityMemoItemResponse } from '@/shared/types'
import { getCommunityMemoColor } from '@/shared/utils'

const WHITEBOARD_PREVIEW_MEMO_LIMIT = 8
const WHITEBOARD_PREVIEW_SAFE_PERCENT = 8
const COMMUNITY_CANVAS_SURFACE_WIDTH =
  COMMUNITY_CANVAS_ATTACHABLE_SURFACE_BOUNDS.right -
  COMMUNITY_CANVAS_ATTACHABLE_SURFACE_BOUNDS.left
const COMMUNITY_CANVAS_SURFACE_HEIGHT =
  COMMUNITY_CANVAS_ATTACHABLE_SURFACE_BOUNDS.bottom -
  COMMUNITY_CANVAS_ATTACHABLE_SURFACE_BOUNDS.top

export type CommunityCanvasWhiteboardPreviewStatus =
  | 'idle'
  | 'loading'
  | 'success'
  | 'error'

export interface CommunityCanvasWhiteboardPreviewMemo {
  id: string
  authorNickname: string
  color: string
  imageUrl: string | null
  isPlaceholder: boolean
  rotationDeg: number
  xPercent: number
  yPercent: number
  zIndex: number
}

const FALLBACK_WHITEBOARD_PREVIEW_MEMOS: CommunityCanvasWhiteboardPreviewMemo[] = [
  {
    id: 'fallback-community-memo-lemon',
    authorNickname: 'NEMONIC',
    color: '#ffe887',
    imageUrl: null,
    isPlaceholder: true,
    rotationDeg: -8,
    xPercent: 22,
    yPercent: 32,
    zIndex: 1,
  },
  {
    id: 'fallback-community-memo-mint',
    authorNickname: 'NEMONIC',
    color: '#dff4b8',
    imageUrl: null,
    isPlaceholder: true,
    rotationDeg: 5,
    xPercent: 46,
    yPercent: 42,
    zIndex: 2,
  },
  {
    id: 'fallback-community-memo-sky',
    authorNickname: 'NEMONIC',
    color: '#cceef6',
    imageUrl: null,
    isPlaceholder: true,
    rotationDeg: -3,
    xPercent: 68,
    yPercent: 30,
    zIndex: 3,
  },
  {
    id: 'fallback-community-memo-peach',
    authorNickname: 'NEMONIC',
    color: '#ffd6b8',
    imageUrl: null,
    isPlaceholder: true,
    rotationDeg: 7,
    xPercent: 34,
    yPercent: 67,
    zIndex: 4,
  },
  {
    id: 'fallback-community-memo-pink',
    authorNickname: 'NEMONIC',
    color: '#ffd0dc',
    imageUrl: null,
    isPlaceholder: true,
    rotationDeg: -6,
    xPercent: 61,
    yPercent: 70,
    zIndex: 5,
  },
]

function clampPercent(value: number) {
  return Math.min(
    Math.max(value, WHITEBOARD_PREVIEW_SAFE_PERCENT),
    100 - WHITEBOARD_PREVIEW_SAFE_PERCENT,
  )
}

function getMemoAttachedTime(memo: CommunityMemoItemResponse) {
  const attachedTime = new Date(memo.attachedAt).getTime()

  return Number.isFinite(attachedTime) ? attachedTime : 0
}

function compareMemosByVisibleStack(
  firstMemo: CommunityMemoItemResponse,
  secondMemo: CommunityMemoItemResponse,
) {
  const zIndexDelta = firstMemo.zIndex - secondMemo.zIndex
  if (zIndexDelta !== 0) return zIndexDelta

  return getMemoAttachedTime(firstMemo) - getMemoAttachedTime(secondMemo)
}

function getWhiteboardPreviewPercent(position: number, minimum: number, surfaceSize: number) {
  return clampPercent(((position - minimum) / surfaceSize) * 100)
}

function getMemoPreviewImageUrl(memo: CommunityMemoItemResponse) {
  return memo.memoThumbnailImageUrl || memo.memoImageUrl || null
}

function toWhiteboardPreviewMemo(
  memo: CommunityMemoItemResponse,
): CommunityCanvasWhiteboardPreviewMemo {
  return {
    id: memo.memoUuid,
    authorNickname: memo.authorNickname,
    color: getCommunityMemoColor(memo),
    imageUrl: getMemoPreviewImageUrl(memo),
    isPlaceholder: false,
    rotationDeg: memo.rotationDeg,
    xPercent: getWhiteboardPreviewPercent(
      memo.positionX,
      COMMUNITY_CANVAS_ATTACHABLE_SURFACE_BOUNDS.left,
      COMMUNITY_CANVAS_SURFACE_WIDTH,
    ),
    yPercent: getWhiteboardPreviewPercent(
      memo.positionY,
      COMMUNITY_CANVAS_ATTACHABLE_SURFACE_BOUNDS.top,
      COMMUNITY_CANVAS_SURFACE_HEIGHT,
    ),
    zIndex: memo.zIndex,
  }
}

function getWhiteboardPreviewMemos(memos: CommunityMemoItemResponse[]) {
  return [...memos]
    .sort(compareMemosByVisibleStack)
    .slice(-WHITEBOARD_PREVIEW_MEMO_LIMIT)
    .map(toWhiteboardPreviewMemo)
}

export function useCommunityCanvasWhiteboardPreviewMemos() {
  const [communityMemos, setCommunityMemos] = useState<CommunityMemoItemResponse[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [status, setStatus] =
    useState<CommunityCanvasWhiteboardPreviewStatus>('idle')

  useEffect(() => {
    let isCancelled = false

    ;(async () => {
      setStatus('loading')

      try {
        const response = await getCommunityMemoList()
        if (isCancelled) return

        setCommunityMemos(response.items)
        setTotalElements(response.totalElements)
        setStatus('success')
      } catch {
        if (isCancelled) return

        setCommunityMemos([])
        setTotalElements(0)
        setStatus('error')
      }
    })()

    return () => {
      isCancelled = true
    }
  }, [])

  const communityPreviewMemos = useMemo(
    () => getWhiteboardPreviewMemos(communityMemos),
    [communityMemos],
  )
  const isUsingFallbackPreview = communityPreviewMemos.length === 0
  const previewMemos = isUsingFallbackPreview
    ? FALLBACK_WHITEBOARD_PREVIEW_MEMOS
    : communityPreviewMemos
  const hiddenMemoCount = isUsingFallbackPreview
    ? 0
    : Math.max(totalElements - previewMemos.length, 0)

  return {
    hiddenMemoCount,
    isUsingFallbackPreview,
    previewMemos,
    status,
  }
}
