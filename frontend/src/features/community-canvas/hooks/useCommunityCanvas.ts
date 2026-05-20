'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { HTTPError } from 'ky'
import { toast } from 'sonner'
import {
  ApiError,
  deleteCommunityMemo,
  getCommunityMemo,
  getCommunityMemoList,
  patchCommunityMemo,
  postCommunityMemoReport,
} from '@/shared/apis'
import { useUserStore } from '@/shared/stores'
import type {
  CommunityMemoDetailResponse,
  CommunityMemoItemResponse,
  CommunityMemoLayoutRequest,
  CommunityMemoReportReason,
} from '@/shared/types'
import {
  isCommunityAnimatedImageUrl,
  preloadCommunityMemoSounds,
  playCommunityMemoAttachSound,
  playCommunityMemoDetachSound,
} from '../utils'

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'
type MemoPlaybackImageUrlMap = Record<string, string>
type MemoWithDecoration = Pick<CommunityMemoItemResponse, 'decoration'>
type ApiErrorResponseBody = {
  message?: unknown
}

const DUPLICATE_REPORT_ERROR_MESSAGE = '이미 신고한 메모는 중복 신고할 수 없어요.'

export interface CommunityMemoLayoutDraft {
  positionX: number
  positionY: number
  zIndex: number
  rotationDeg: number
}

function toErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) return error.message || fallback
  if (error instanceof Error) return error.message || fallback
  return fallback
}

function isDuplicateReportMessage(message: string) {
  return message.includes('중복') || (message.includes('이미') && message.includes('신고'))
}

async function toHttpErrorResponseMessage(error: HTTPError) {
  try {
    const responseBody = (await error.response.clone().json()) as ApiErrorResponseBody
    return typeof responseBody.message === 'string' ? responseBody.message : null
  } catch {
    return null
  }
}

async function toReportErrorMessage(error: unknown) {
  if (error instanceof ApiError && isDuplicateReportMessage(error.message)) {
    return DUPLICATE_REPORT_ERROR_MESSAGE
  }

  if (error instanceof HTTPError) {
    const responseMessage = await toHttpErrorResponseMessage(error)
    if (responseMessage && isDuplicateReportMessage(responseMessage)) {
      return DUPLICATE_REPORT_ERROR_MESSAGE
    }

    if (error.response.status === 404 || error.response.status === 409) {
      return DUPLICATE_REPORT_ERROR_MESSAGE
    }
  }

  return toErrorMessage(error, '신고 접수에 실패했어요.')
}

function toLayoutDraft(memo: CommunityMemoItemResponse): CommunityMemoLayoutDraft {
  return {
    positionX: memo.positionX,
    positionY: memo.positionY,
    zIndex: memo.zIndex,
    rotationDeg: memo.rotationDeg,
  }
}

function applyMemoDetailLayout(
  memo: CommunityMemoItemResponse,
  detail: CommunityMemoDetailResponse,
): CommunityMemoItemResponse {
  return {
    ...memo,
    positionX: detail.positionX,
    positionY: detail.positionY,
    zIndex: detail.zIndex,
    rotationDeg: detail.rotationDeg,
    decoration: memo.decoration ?? detail.decoration,
  }
}

function getDecorationString(decoration: unknown, key: string) {
  if (!decoration || typeof decoration !== 'object') return null
  const value = (decoration as Record<string, unknown>)[key]
  return typeof value === 'string' && value.length > 0 ? value : null
}

function getDecorationPlaybackImageUrl(memo: MemoWithDecoration) {
  const sourceContentKind = getDecorationString(memo.decoration, 'sourceContentKind')
  const playbackImageUrl =
    getDecorationString(memo.decoration, 'sourcePlaybackImageUrl') ||
    getDecorationString(memo.decoration, 'playbackImageUrl') ||
    getDecorationString(memo.decoration, 'sourceImageUrl')

  if (!playbackImageUrl) return null

  if (
    isCommunityAnimatedImageUrl(playbackImageUrl) ||
    sourceContentKind?.toLowerCase() === 'flipbook'
  ) {
    return playbackImageUrl
  }

  return null
}

function getMemoGifImageUrl(memo: CommunityMemoItemResponse) {
  const gifImageUrl = [
    memo.memoOriginalImageUrl,
    memo.memoImageUrl,
    memo.memoThumbnailImageUrl,
  ].find(isCommunityAnimatedImageUrl)

  return gifImageUrl ?? null
}

function getAnimatedDetailImageUrl(detail: CommunityMemoDetailResponse) {
  if (detail.memoPlaybackImageUrl) return detail.memoPlaybackImageUrl
  const decorationPlaybackImageUrl = getDecorationPlaybackImageUrl(detail)
  if (decorationPlaybackImageUrl) return decorationPlaybackImageUrl
  return getMemoGifImageUrl(detail)
}

function getAnimatedMemoImageUrl(memo: CommunityMemoItemResponse) {
  if (memo.memoPlaybackImageUrl) return memo.memoPlaybackImageUrl
  const decorationPlaybackImageUrl = getDecorationPlaybackImageUrl(memo)
  if (decorationPlaybackImageUrl) return decorationPlaybackImageUrl
  return getMemoGifImageUrl(memo)
}

export function useCommunityCanvas() {
  const userUuid = useUserStore((state) => state.userUuid)
  const detailRequestIdRef = useRef(0)
  const memoPlaybackRequestIdRef = useRef(0)
  const [memos, setMemos] = useState<CommunityMemoItemResponse[]>([])
  const [memoPlaybackImageUrls, setMemoPlaybackImageUrls] =
    useState<MemoPlaybackImageUrlMap>({})
  const [memoStatus, setMemoStatus] = useState<AsyncStatus>('idle')
  const [memoError, setMemoError] = useState<string | null>(null)
  const [selectedWallMemoUuid, setSelectedWallMemoUuid] = useState<string | null>(null)
  const [selectedMemoUuid, setSelectedMemoUuid] = useState<string | null>(null)
  const [selectedMemoDetail, setSelectedMemoDetail] =
    useState<CommunityMemoDetailResponse | null>(null)
  const [selectedMemoPlaybackImageUrl, setSelectedMemoPlaybackImageUrl] =
    useState<string | null>(null)
  const [detailStatus, setDetailStatus] = useState<AsyncStatus>('idle')
  const [detailError, setDetailError] = useState<string | null>(null)
  const [editingMemo, setEditingMemo] = useState<CommunityMemoItemResponse | null>(null)
  const [editingLayoutDraft, setEditingLayoutDraft] =
    useState<CommunityMemoLayoutDraft | null>(null)
  const [mutationStatus, setMutationStatus] = useState<AsyncStatus>('idle')
  const [reportStatus, setReportStatus] = useState<AsyncStatus>('idle')

  useEffect(() => {
    preloadCommunityMemoSounds()
  }, [])

  const resolveMemoPlaybackImageUrls = useCallback(
    (nextMemos: CommunityMemoItemResponse[], requestId: number) => {
      const playbackEntries = nextMemos.map((memo) => {
        const animatedImageUrl = getAnimatedMemoImageUrl(memo)
        return animatedImageUrl ? ([memo.memoUuid, animatedImageUrl] as const) : null
      })

      if (memoPlaybackRequestIdRef.current !== requestId) return

      setMemoPlaybackImageUrls((currentPlaybackImageUrls) => {
        const nextPlaybackImageUrls = { ...currentPlaybackImageUrls }

        playbackEntries.forEach((entry) => {
          if (!entry) return
          const [memoUuid, animatedImageUrl] = entry
          nextPlaybackImageUrls[memoUuid] = animatedImageUrl
        })

        return nextPlaybackImageUrls
      })
    },
    [],
  )

  const loadCommunityMemos = useCallback(async () => {
    const requestId = memoPlaybackRequestIdRef.current + 1
    memoPlaybackRequestIdRef.current = requestId
    setMemoStatus('loading')
    setMemoError(null)

    try {
      const response = await getCommunityMemoList()
      setMemos(response.items)
      setMemoPlaybackImageUrls((currentPlaybackImageUrls) => {
        const nextPlaybackImageUrls: MemoPlaybackImageUrlMap = {}

        response.items.forEach((memo) => {
          const playbackImageUrl =
            memo.memoPlaybackImageUrl || currentPlaybackImageUrls[memo.memoUuid]

          if (playbackImageUrl) {
            nextPlaybackImageUrls[memo.memoUuid] = playbackImageUrl
          }
        })

        return nextPlaybackImageUrls
      })
      setMemoStatus('success')
      void resolveMemoPlaybackImageUrls(response.items, requestId)
    } catch (error) {
      setMemoError(toErrorMessage(error, '커뮤니티 메모를 불러오지 못했어요.'))
      setMemoStatus('error')
    }
  }, [resolveMemoPlaybackImageUrls])

  const loadCommunityMemosWithCreatedMemo = useCallback(
    async (createdMemo: CommunityMemoDetailResponse) => {
      await loadCommunityMemos()
      setMemos((currentMemos) =>
        currentMemos.map((memo) =>
          memo.memoUuid === createdMemo.memoUuid
            ? applyMemoDetailLayout(memo, createdMemo)
            : memo,
        ),
      )
    },
    [loadCommunityMemos],
  )

  useEffect(() => {
    let cancelled = false

    void (async () => {
      await Promise.resolve()
      if (!cancelled) {
        await loadCommunityMemos()
      }
    })()

    return () => {
      cancelled = true
    }
  }, [loadCommunityMemos, userUuid])

  const openMemoDetail = useCallback(async (memoUuid: string) => {
    const requestId = detailRequestIdRef.current + 1
    detailRequestIdRef.current = requestId

    setSelectedWallMemoUuid(memoUuid)
    setSelectedMemoUuid(memoUuid)
    setDetailStatus('loading')
    setDetailError(null)
    setSelectedMemoDetail(null)
    setSelectedMemoPlaybackImageUrl(null)
    setEditingMemo(null)
    setEditingLayoutDraft(null)

    try {
      const detail = await getCommunityMemo(memoUuid)
      if (detailRequestIdRef.current !== requestId) return

      setSelectedMemoDetail(detail)
      setDetailStatus('success')

      const animatedImageUrl = getAnimatedDetailImageUrl(detail)
      if (detailRequestIdRef.current !== requestId) return

      setSelectedMemoPlaybackImageUrl(animatedImageUrl)
    } catch (error) {
      if (detailRequestIdRef.current !== requestId) return

      setDetailError(toErrorMessage(error, '메모 상세를 불러오지 못했어요.'))
      setDetailStatus('error')
    }
  }, [])

  const closeMemoDetail = useCallback(() => {
    detailRequestIdRef.current += 1
    setSelectedMemoUuid(null)
    setSelectedMemoDetail(null)
    setSelectedMemoPlaybackImageUrl(null)
    setDetailStatus('idle')
    setDetailError(null)
  }, [])

  const selectWallMemo = useCallback((memo: CommunityMemoItemResponse) => {
    detailRequestIdRef.current += 1
    setSelectedWallMemoUuid(memo.memoUuid)
    setSelectedMemoUuid(null)
    setSelectedMemoDetail(null)
    setSelectedMemoPlaybackImageUrl(null)
    setDetailStatus('idle')
    setDetailError(null)

    if (!memo.ownedByMe) {
      setEditingMemo(null)
      setEditingLayoutDraft(null)
      return
    }

    const isEnteringLayoutEdit = editingMemo?.memoUuid !== memo.memoUuid || !editingLayoutDraft
    if (isEnteringLayoutEdit) {
      playCommunityMemoDetachSound()
    }

    setEditingMemo(memo)
    setEditingLayoutDraft(toLayoutDraft(memo))
    setMutationStatus('idle')
  }, [editingLayoutDraft, editingMemo])

  const clearWallMemoSelection = useCallback(() => {
    setSelectedWallMemoUuid(null)
    setEditingMemo(null)
    setEditingLayoutDraft(null)
    setMutationStatus('idle')
  }, [])

  const startSelectedMemoLayoutEdit = useCallback(() => {
    if (!selectedMemoDetail?.ownedByMe) return

    detailRequestIdRef.current += 1
    setSelectedWallMemoUuid(selectedMemoDetail.memoUuid)
    setEditingMemo(selectedMemoDetail)
    setEditingLayoutDraft(toLayoutDraft(selectedMemoDetail))
    playCommunityMemoDetachSound()
    setSelectedMemoUuid(null)
    setSelectedMemoDetail(null)
    setSelectedMemoPlaybackImageUrl(null)
    setDetailStatus('idle')
    setDetailError(null)
  }, [selectedMemoDetail])

  const updateEditingLayoutDraft = useCallback((partialLayout: Partial<CommunityMemoLayoutDraft>) => {
    setEditingLayoutDraft((currentDraft) => {
      if (!currentDraft) return currentDraft
      return { ...currentDraft, ...partialLayout }
    })
  }, [])

  const cancelEditingMemoLayout = useCallback(() => {
    setEditingMemo(null)
    setEditingLayoutDraft(null)
    setMutationStatus('idle')
  }, [])

  const saveEditingMemoLayout = useCallback(async (overrideLayout?: CommunityMemoLayoutDraft) => {
    if (!editingMemo || !editingLayoutDraft) return

    const layoutToSave = overrideLayout ?? editingLayoutDraft

    const payload: CommunityMemoLayoutRequest = {
      positionX: layoutToSave.positionX,
      positionY: layoutToSave.positionY,
      zIndex: layoutToSave.zIndex,
      rotationDeg: layoutToSave.rotationDeg,
    }

    setMutationStatus('loading')
    try {
      const updatedMemo = await patchCommunityMemo(editingMemo.memoUuid, payload)
      await loadCommunityMemos()
      setMemos((currentMemos) =>
        currentMemos.map((memo) =>
          memo.memoUuid === updatedMemo.memoUuid
            ? applyMemoDetailLayout(memo, updatedMemo)
            : memo,
        ),
      )
      setSelectedWallMemoUuid(editingMemo.memoUuid)
      setEditingMemo(null)
      setEditingLayoutDraft(null)
      setMutationStatus('success')
      playCommunityMemoAttachSound()
      toast.success('메모 위치를 저장했어요.')
    } catch (error) {
      setMutationStatus('error')
      toast.error(toErrorMessage(error, '메모 위치 저장에 실패했어요.'))
    }
  }, [editingLayoutDraft, editingMemo, loadCommunityMemos])

  const deleteSelectedMemo = useCallback(async () => {
    if (!selectedMemoUuid) return

    setMutationStatus('loading')
    try {
      await deleteCommunityMemo(selectedMemoUuid)
      setSelectedWallMemoUuid(null)
      closeMemoDetail()
      await loadCommunityMemos()
      setMutationStatus('success')
      playCommunityMemoDetachSound()
      toast.success('메모를 벽에서 떼어냈어요.')
    } catch (error) {
      setMutationStatus('error')
      toast.error(toErrorMessage(error, '메모 삭제에 실패했어요.'))
    }
  }, [closeMemoDetail, loadCommunityMemos, selectedMemoUuid])

  const reportSelectedMemo = useCallback(
    async (reason: CommunityMemoReportReason, reasonDetail: string) => {
      if (!selectedMemoUuid) return false

      setReportStatus('loading')
      try {
        await postCommunityMemoReport(selectedMemoUuid, {
          reason,
          reasonDetail,
        })
        await loadCommunityMemos()
        setReportStatus('success')
        toast.success('신고를 접수했어요.')
        return true
      } catch (error) {
        setReportStatus('error')
        toast.error(await toReportErrorMessage(error))
        return false
      }
    },
    [loadCommunityMemos, selectedMemoUuid],
  )

  const nextZIndex = useMemo(() => {
    const maxZIndex = memos.reduce(
      (currentMax, memo) => Math.max(currentMax, memo.zIndex),
      0,
    )
    return maxZIndex + 1
  }, [memos])

  return {
    memos,
    memoPlaybackImageUrls,
    memoStatus,
    memoError,
    selectedWallMemoUuid,
    selectedMemoUuid,
    selectedMemoDetail,
    selectedMemoPlaybackImageUrl,
    detailStatus,
    detailError,
    editingMemo,
    editingLayoutDraft,
    mutationStatus,
    reportStatus,
    nextZIndex,
    loadCommunityMemos,
    loadCommunityMemosWithCreatedMemo,
    selectWallMemo,
    clearWallMemoSelection,
    openMemoDetail,
    closeMemoDetail,
    startSelectedMemoLayoutEdit,
    updateEditingLayoutDraft,
    cancelEditingMemoLayout,
    saveEditingMemoLayout,
    deleteSelectedMemo,
    reportSelectedMemo,
  }
}
