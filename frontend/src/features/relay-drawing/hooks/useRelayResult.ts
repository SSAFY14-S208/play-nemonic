'use client'

import { useEffect, useMemo, useRef, useState } from 'react'

import { ApiError, getArtifactDownload, postRelayRoomClose } from '@/shared/apis'
import { reachFunnelGoal } from '@/shared/libs'
import { useUserStore } from '@/shared/stores'
import type { RelayPart } from '@/shared/types'
import {
  downloadBlob,
  inferImageExtensionFromBlob,
  sanitizeDownloadFilename,
} from '@/shared/utils'

import {
  RELAY_ROUND_RULES,
  SEGMENT_TAG_CLASSNAMES,
  type RelayResultSegment,
  type RelayRoundKey,
} from '../constants'
import { useRelayDrawingStore } from '../stores'

// ── 유틸 ──────────────────────────────────────────────────────────────

function partToRoundKey(part: RelayPart): RelayRoundKey {
  return part.toLowerCase() as RelayRoundKey
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

  const downloadActiveArtifact = () => {
    if (isDownloading) return
    const currentActiveResult = resultItems[activeResultIndex] ?? null
    if (!currentActiveResult) return
    setDownloadError(null)
    setIsDownloading(true)
    void (async () => {
      try {
        const blob = await getArtifactDownload(currentActiveResult.artifactId)
        const faceDrawerNickname =
          currentActiveResult.parts.find((partItem) => partItem.part === 'FACE')?.drawerNickname
        const baseFilename = sanitizeDownloadFilename(
          faceDrawerNickname
            ? `${faceDrawerNickname}의 릴레이 드로잉`
            : `릴레이 드로잉 ${currentActiveResult.canvasIndex + 1}`,
        )
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

  const activeResultItem = resultItems[activeResultIndex] ?? null
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
  }
}
