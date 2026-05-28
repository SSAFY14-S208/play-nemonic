'use client'

import { useMemo } from 'react'

import { useUserStore } from '@/shared/stores'

import { useRelayDrawingStore } from '@/features/relay-drawing/stores'

import { getActiveResultTitle, getResultSegments } from '../relayResultUtils'
import { useRelayResultDownload } from './useRelayResultDownload'
import { useRelayResultExternalShare } from './useRelayResultExternalShare'
import { useRelayResultGoal } from './useRelayResultGoal'
import { useRelayRoomClose } from './useRelayRoomClose'

export function useRelayResult() {
  const roomCode = useRelayDrawingStore((state) => state.roomCode)
  const hostUserUuid = useRelayDrawingStore((state) => state.hostUserUuid)
  const resultItems = useRelayDrawingStore((state) => state.resultItems)
  const activeResultIndex = useRelayDrawingStore((state) => state.activeResultIndex)
  const setActiveResultIndex = useRelayDrawingStore((state) => state.setActiveResultIndex)

  const currentUserUuid = useUserStore((state) => state.userUuid)
  const isHost = currentUserUuid !== null && currentUserUuid === hostUserUuid

  const { isClosingRoom, closeRoomError, closeRoom } = useRelayRoomClose({ roomCode, isHost })

  const activeResultItem = resultItems[activeResultIndex] ?? null
  const activeResultTitle = getActiveResultTitle(activeResultItem)
  const hasServerResults = resultItems.length > 0 && activeResultItem !== null

  const { isDownloading, downloadError, downloadActiveArtifact } = useRelayResultDownload({
    activeResultItem,
    activeResultTitle,
  })
  const { isSharingExternal, canShareExternal, shareActiveArtifact } =
    useRelayResultExternalShare({
      activeResultItem,
      activeResultTitle,
    })

  useRelayResultGoal({ hasServerResults, roomCode })

  const { segments, resultImageUrl } = useMemo(
    () => getResultSegments({ activeResultItem, currentUserUuid }),
    [activeResultItem, currentUserUuid],
  )

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
