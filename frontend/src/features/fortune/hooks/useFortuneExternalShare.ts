'use client'

import { useCallback, useState } from 'react'
import { toast } from 'sonner'

import { ApiError, postArtifactShare } from '@/shared/apis'
import { logEvent } from '@/shared/libs'
import { shareExternalImage, type ExternalImageShareResult } from '@/shared/utils'

import { useFortuneSessionStore } from '../fortuneSessionStore'

const FORTUNE_SHARE_TEXT = '네모닉 운세 결과를 공유해요.'
const DEFAULT_SHARE_ERROR_MESSAGE = '운세 공유 정보를 만들 수 없어요.'
const GIF_LINK_COPIED_MESSAGE = '운세 QR GIF 공유 링크를 복사했어요.'
const IMAGE_COPIED_MESSAGE =
  '운세 QR 공유 이미지를 복사했어요. 채팅창에 붙여넣어 주세요.'
const IMAGE_LINK_COPIED_MESSAGE = '운세 QR 공유 이미지 링크를 복사했어요.'

function isServerFortuneId(fortuneId: string | null | undefined) {
  return Boolean(fortuneId && !fortuneId.startsWith('fortune-'))
}

function toExternalShareErrorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message || DEFAULT_SHARE_ERROR_MESSAGE
  if (error instanceof Error) return error.message || DEFAULT_SHARE_ERROR_MESSAGE

  return DEFAULT_SHARE_ERROR_MESSAGE
}

function getExternalShareSuccessMessage(shareResult: ExternalImageShareResult) {
  if (shareResult === 'copied-gif-link') return GIF_LINK_COPIED_MESSAGE
  if (shareResult === 'copied-image') return IMAGE_COPIED_MESSAGE
  if (shareResult === 'copied-image-link') return IMAGE_LINK_COPIED_MESSAGE

  return null
}

export function useFortuneExternalShare() {
  const result = useFortuneSessionStore((state) => state.result)
  const [isSharingExternal, setIsSharingExternal] = useState(false)
  const canShareExternal = Boolean(result && isServerFortuneId(result.id)) && !isSharingExternal

  const shareExternal = useCallback(async () => {
    if (!result || !isServerFortuneId(result.id) || isSharingExternal) {
      toast.error('운세 공유는 서버에 저장된 운세에서만 사용할 수 있어요.')
      return
    }

    setIsSharingExternal(true)

    try {
      const shareInfo = await postArtifactShare(result.id)
      if (!shareInfo.imageUrl?.trim()) {
        throw new Error('운세 공유 이미지를 만들지 못했어요.')
      }

      const shareResult = await shareExternalImage({
        title: result.title,
        text: FORTUNE_SHARE_TEXT,
        imageUrl: shareInfo.imageUrl,
        fileNameBase: 'fortune-result-qr',
        preferNativeFileShare: true,
      })
      const successMessage = getExternalShareSuccessMessage(shareResult)
      if (successMessage) toast.success(successMessage)
      logEvent('result_shared', {
        metadata: {
          funnel_name: 'fortune_creation',
          content_type: 'fortune',
          share_method: 'external_share',
          fortune_id: result.id,
        },
      })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return

      toast.error(toExternalShareErrorMessage(error))
    } finally {
      setIsSharingExternal(false)
    }
  }, [result, isSharingExternal])

  return {
    canShareExternal,
    isSharingExternal,
    shareExternal,
  }
}
