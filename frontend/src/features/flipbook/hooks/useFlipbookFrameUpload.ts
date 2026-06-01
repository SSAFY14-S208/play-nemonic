'use client'

import { useCallback } from 'react'

import {
  postFileConfirm,
  postFilePresign,
  putFileToPresignedUrl,
} from '@/shared/apis'
import type { DrawingLine } from '@/shared/types'
import {
  createCanvasBlobFromLines,
  FLIPBOOK_FILE_CONTENT_TYPE,
  FLIPBOOK_FILE_PURPOSE,
} from '../utils'

export function useFlipbookFrameUpload() {
  return useCallback(async (lines: DrawingLine[], round: number) => {
    const imageBlob = await createCanvasBlobFromLines(lines)
    const fileName = `flipbook-round-${round}-${Date.now()}.png`
    const presigned = await postFilePresign({
      fileName,
      contentType: FLIPBOOK_FILE_CONTENT_TYPE,
      purpose: FLIPBOOK_FILE_PURPOSE,
      byteSize: imageBlob.size,
    })

    await putFileToPresignedUrl({
      presignedUrl: presigned.presignedUrl,
      file: imageBlob,
      contentType: FLIPBOOK_FILE_CONTENT_TYPE,
    })
    await postFileConfirm(presigned.fileId)

    return presigned.fileId
  }, [])
}

export type FlipbookFrameUpload = ReturnType<typeof useFlipbookFrameUpload>
