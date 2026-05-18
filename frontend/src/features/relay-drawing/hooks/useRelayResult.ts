'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { toast } from 'sonner'

import {
  ApiError,
  getArtifactDownload,
  postArtifactShare,
  postRelayRoomClose,
} from '@/shared/apis'
import { reachFunnelGoal } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'
import type { RelayPart } from '@/shared/types'
import {
  downloadBlob,
  type ExternalImageShareResult,
  inferImageExtensionFromBlob,
  sanitizeDownloadFilename,
  shareExternalImage,
} from '@/shared/utils'

import {
  RELAY_ROUND_RULES,
  SEGMENT_TAG_CLASSNAMES,
  type RelayResultSegment,
  type RelayRoundKey,
} from '../constants'
import { useRelayDrawingStore } from '../stores'

// ── 유틸 ──────────────────────────────────────────────────────────────

const RELAY_EXTERNAL_SHARE_TEXT = '네모닉 릴레이 드로잉 결과를 공유해요.'
const RELAY_SHARE_IMAGE_COPIED_MESSAGE =
  '릴레이 드로잉 QR 공유 이미지를 복사했어요. 채팅창에 붙여 넣어 주세요.'
const RELAY_SHARE_IMAGE_LINK_COPIED_MESSAGE =
  '릴레이 드로잉 QR 공유 이미지 링크를 복사했어요.'
const RELAY_SHARE_GIF_LINK_COPIED_MESSAGE =
  '릴레이 드로잉 QR GIF 공유 링크를 복사했어요.'

function partToRoundKey(part: RelayPart): RelayRoundKey {
  return part.toLowerCase() as RelayRoundKey
}

function isRealArtifactId(artifactId: string | null | undefined) {
  return Boolean(artifactId && !artifactId.startsWith('dummy-'))
}

function getResultTitle({
  faceDrawerNickname,
  canvasIndex,
}: {
  faceDrawerNickname: string | null | undefined
  canvasIndex: number
}) {
  return faceDrawerNickname
    ? `${faceDrawerNickname}의 릴레이 드로잉`
    : `릴레이 드로잉 ${canvasIndex + 1}`
}

function getExternalShareSuccessMessage(shareResult: ExternalImageShareResult) {
  if (shareResult === 'copied-gif-link') return RELAY_SHARE_GIF_LINK_COPIED_MESSAGE
  if (shareResult === 'copied-image') return RELAY_SHARE_IMAGE_COPIED_MESSAGE
  if (shareResult === 'copied-image-link') return RELAY_SHARE_IMAGE_LINK_COPIED_MESSAGE

  return null
}

function toExternalShareErrorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message || '외부 공유 정보를 만들 수 없어요.'
  if (error instanceof Error) {
    if (error.message === 'file-share-unavailable') {
      return '이 브라우저에서는 이미지 파일 공유를 사용할 수 없어요.'
    }

    return error.message || '외부 공유 정보를 만들 수 없어요.'
  }

  return '외부 공유 정보를 만들 수 없어요.'
}

// ── 훅 ────────────────────────────────────────────────────────────────

export function useRelayResult() {
  const roomCode = useRelayDrawingStore((state) => state.roomCode)
  const hostUserUuid = useRelayDrawingStore((state) => state.hostUserUuid)
  const resultItems = useRelayDrawingStore((state) => state.resultItems)
  const activeResultIndex = useRelayDrawingStore((state) => state.activeResultIndex)
  const setActiveResultIndex = useRelayDrawingStore((state) => state.setActiveResultIndex)

  const currentUserUuid = useUserStore((state) => state.userUuid)
  const isHost = currentUserUuid !== null && currentUserUuid === hostUserUuid

  // 호스트 전용 방 종료 — 가이드 §21.
  // 성공 시 ROOM_CLOSED WS 이벤트가 도착해 dismissalReason이 세팅되고,
  // RelayDismissalModal이 자동으로 안내한다. 여기서는 store를 직접 건드리지 않는다.
  const [isClosingRoom, setIsClosingRoom] = useState(false)
  const [closeRoomError, setCloseRoomError] = useState<string | null>(null)

  const closeRoom = () => {
    if (!roomCode || !isHost || isClosingRoom) return
    setCloseRoomError(null)
    setIsClosingRoom(true)
    void (async () => {
      try {
        await postRelayRoomClose(roomCode)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '방 종료에 실패했어요'
        setCloseRoomError(message)
      } finally {
        setIsClosingRoom(false)
      }
    })()
  }

  // 현재 활성 작품을 디바이스에 다운로드. GET /artifacts/{artifactId}/download가
  // raw Blob을 반환하므로 브라우저 a[download] 트리거로 OS 저장 다이얼로그를 띄움.
  // 중복 클릭 방지를 위해 isDownloading 상태로 가드.
  const [isDownloading, setIsDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)

  const activeResultItem = resultItems[activeResultIndex] ?? null
  const faceDrawerNickname =
    activeResultItem?.parts.find((partItem) => partItem.part === 'FACE')?.drawerNickname ?? null
  const activeResultTitle = activeResultItem
    ? getResultTitle({
        faceDrawerNickname,
        canvasIndex: activeResultItem.canvasIndex,
      })
    : '릴레이 드로잉'

  const downloadActiveArtifact = () => {
    if (isDownloading) return
    if (!activeResultItem) return
    setDownloadError(null)
    setIsDownloading(true)
    void (async () => {
      try {
        const blob = await getArtifactDownload(activeResultItem.artifactId)
        const baseFilename = sanitizeDownloadFilename(activeResultTitle)
        const extension = inferImageExtensionFromBlob(blob)
        downloadBlob(blob, `${baseFilename}.${extension}`)
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '다운로드에 실패했어요'
        setDownloadError(message)
      } finally {
        setIsDownloading(false)
      }
    })()
  }

  const [isSharingExternal, setIsSharingExternal] = useState(false)
  const canShareExternal =
    Boolean(activeResultItem && isRealArtifactId(activeResultItem.artifactId)) &&
    !isSharingExternal

  const shareActiveArtifact = () => {
    if (!activeResultItem || !isRealArtifactId(activeResultItem.artifactId) || isSharingExternal) {
      toast.error('외부 공유는 저장된 결과에서만 사용할 수 있어요.')
      return
    }

    setIsSharingExternal(true)
    void (async () => {
      try {
        const shareInfo = await postArtifactShare(activeResultItem.artifactId)
        if (!shareInfo.imageUrl?.trim()) {
          throw new Error('외부 공유 이미지를 찾지 못했어요.')
        }

        const shareResult = await shareExternalImage({
          title: activeResultTitle,
          text: RELAY_EXTERNAL_SHARE_TEXT,
          imageUrl: shareInfo.imageUrl,
          fileNameBase: 'relay-drawing-result-qr',
          preferNativeFileShare: true,
        })
        const successMessage = getExternalShareSuccessMessage(shareResult)
        if (successMessage) toast.success(successMessage)
      } catch (caughtError) {
        if (caughtError instanceof DOMException && caughtError.name === 'AbortError') return

        toast.error(toExternalShareErrorMessage(caughtError))
      } finally {
        setIsSharingExternal(false)
      }
    })()
  }

  const hasServerResults = resultItems.length > 0 && activeResultItem !== null

  // 결과 화면 도달 — funnel goal. 결과 데이터가 도착한 시점 1회만 발사.
  const goalFiredRef = useRef(false)
  useEffect(() => {
    if (!hasServerResults || goalFiredRef.current) return
    goalFiredRef.current = true
    reachFunnelGoal('result_viewed', {
      content_type: 'relay',
      room_id: roomCode ?? undefined,
    })
  }, [hasServerResults, roomCode])

  // 서버 결과 데이터로 segments + resultImageUrl 도출.
  const { segments, resultImageUrl } = useMemo(() => {
    if (!activeResultItem) {
      return {
        segments: [] as RelayResultSegment[],
        resultImageUrl: null as string | null,
      }
    }

    const dynamicSegments: RelayResultSegment[] = activeResultItem.parts.map(
      (partItem) => {
        const roundKey = partToRoundKey(partItem.part)
        const roundRule = RELAY_ROUND_RULES[roundKey]
        const isMe = partItem.drawerUserUuid === currentUserUuid
        const displayName = isMe
          ? `${partItem.drawerNickname} (나)`
          : partItem.drawerNickname

        return {
          key: roundKey,
          participantName: displayName,
          roleLabel: roundRule.label,
          tagLabel: `${partItem.drawerNickname} · ${roundRule.label}`,
          tagClassName: SEGMENT_TAG_CLASSNAMES[roundKey],
        }
      },
    )

    return {
      segments: dynamicSegments,
      resultImageUrl: activeResultItem.contentUrl ?? null,
    }
  }, [activeResultItem, currentUserUuid])

  return {
    // Result data
    hasServerResults,
    resultItems,
    activeResultIndex,
    setActiveResultIndex,
    resultImageUrl,
    segments,

    // Host actions
    isHost,
    isClosingRoom,
    closeRoomError,
    closeRoom,

    // Download action
    isDownloading,
    downloadError,
    downloadActiveArtifact,

    // External share action
    isSharingExternal,
    canShareExternal,
    shareActiveArtifact,
  }
}
