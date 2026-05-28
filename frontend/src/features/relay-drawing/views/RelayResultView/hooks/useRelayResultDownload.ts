'use client'

import { useState } from 'react'

import { ApiError, getArtifactDownload } from '@/shared/apis'
import { logEvent } from '@/shared/libs'
import type { RelayRoomResultItemResponse } from '@/shared/types'
import {
  downloadBlob,
  inferImageExtensionFromBlob,
  sanitizeDownloadFilename,
} from '@/shared/utils'

interface UseRelayResultDownloadParams {
  activeResultItem: RelayRoomResultItemResponse | null
  activeResultTitle: string
}

export function useRelayResultDownload({
  activeResultItem,
  activeResultTitle,
}: UseRelayResultDownloadParams) {
  const [isDownloading, setIsDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)

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
        logEvent('result_shared', {
          metadata: {
            funnel_name: 'relay_room_creation',
            content_type: 'relay',
            share_method: 'download',
            artifact_id: activeResultItem.artifactId,
          },
        })
      } catch (caughtError) {
        const message =
          caughtError instanceof ApiError
            ? caughtError.message
            : '이미지를 다운로드하지 못했습니다.'
        setDownloadError(message)
      } finally {
        setIsDownloading(false)
      }
    })()
  }

  return {
    isDownloading,
    downloadError,
    downloadActiveArtifact,
  }
}
