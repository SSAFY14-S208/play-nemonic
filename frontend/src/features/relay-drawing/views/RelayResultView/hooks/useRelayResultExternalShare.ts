'use client'

import { useState } from 'react'
import { toast } from 'sonner'

import { postArtifactShare } from '@/shared/apis'
import { logEvent } from '@/shared/libs'
import type { RelayRoomResultItemResponse } from '@/shared/types'
import { shareExternalImage } from '@/shared/utils'

import {
  RELAY_EXTERNAL_SHARE_TEXT,
  getExternalShareSuccessMessage,
  isRealArtifactId,
  toExternalShareErrorMessage,
} from '../relayResultUtils'

interface UseRelayResultExternalShareParams {
  activeResultItem: RelayRoomResultItemResponse | null
  activeResultTitle: string
}

export function useRelayResultExternalShare({
  activeResultItem,
  activeResultTitle,
}: UseRelayResultExternalShareParams) {
  const [isSharingExternal, setIsSharingExternal] = useState(false)
  const canShareExternal =
    Boolean(activeResultItem && isRealArtifactId(activeResultItem.artifactId)) &&
    !isSharingExternal

  const shareActiveArtifact = () => {
    if (!activeResultItem || !isRealArtifactId(activeResultItem.artifactId) || isSharingExternal) {
      toast.error('공유 가능한 결과가 아직 준비되지 않았습니다.')
      return
    }

    setIsSharingExternal(true)
    void (async () => {
      try {
        const shareInfo = await postArtifactShare(activeResultItem.artifactId)
        if (!shareInfo.imageUrl?.trim()) {
          throw new Error('공유 이미지 URL을 찾지 못했습니다.')
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

        logEvent('result_shared', {
          metadata: {
            funnel_name: 'relay_room_creation',
            content_type: 'relay',
            share_method: 'external_share',
            artifact_id: activeResultItem.artifactId,
          },
        })
      } catch (caughtError) {
        if (caughtError instanceof DOMException && caughtError.name === 'AbortError') return

        toast.error(toExternalShareErrorMessage(caughtError))
      } finally {
        setIsSharingExternal(false)
      }
    })()
  }

  return {
    isSharingExternal,
    canShareExternal,
    shareActiveArtifact,
  }
}
