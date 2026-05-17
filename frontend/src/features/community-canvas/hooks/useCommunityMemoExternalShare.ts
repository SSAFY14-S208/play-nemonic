'use client'

import { useCallback, useState } from 'react'
import { toast } from 'sonner'
import { ApiError, postCommunityMemoShare } from '@/shared/apis'
import type { CommunityMemoDetailResponse } from '@/shared/types'
import { shareExternalImage, type ExternalImageShareResult } from '@/shared/utils'

const DEFAULT_SHARE_ERROR_MESSAGE =
  '\uacf5\uc720 \uc815\ubcf4\ub97c \ub9cc\ub4e4\uc9c0 \ubabb\ud588\uc5b4\uc694.'
const DEFAULT_AUTHOR_NICKNAME = '\ucee4\ubba4\ub2c8\ud2f0'
const SHARE_TEXT =
  '\ub124\ubaa8\ub2c9 \ucee4\ubba4\ub2c8\ud2f0 \uce94\ubc84\uc2a4 \uba54\ubaa8\ub97c \uacf5\uc720\ud574\uc694.'
const GIF_LINK_COPIED_MESSAGE =
  'QR GIF \uacf5\uc720 \ub9c1\ud06c\ub97c \ubcf5\uc0ac\ud588\uc5b4\uc694.'
const IMAGE_COPIED_MESSAGE =
  'QR \uacf5\uc720 \uc774\ubbf8\uc9c0\ub97c \ubcf5\uc0ac\ud588\uc5b4\uc694. \ucc44\ud305\ucc3d\uc5d0 \ubd99\uc5ec\ub123\uc5b4 \uc8fc\uc138\uc694.'
const IMAGE_LINK_COPIED_MESSAGE =
  'QR \uacf5\uc720 \uc774\ubbf8\uc9c0 \ub9c1\ud06c\ub97c \ubcf5\uc0ac\ud588\uc5b4\uc694.'

function toErrorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message || DEFAULT_SHARE_ERROR_MESSAGE
  if (error instanceof Error) return error.message || DEFAULT_SHARE_ERROR_MESSAGE
  return DEFAULT_SHARE_ERROR_MESSAGE
}

function getShareSuccessMessage(shareResult: ExternalImageShareResult) {
  if (shareResult === 'copied-gif-link') return GIF_LINK_COPIED_MESSAGE
  if (shareResult === 'copied-image') return IMAGE_COPIED_MESSAGE
  if (shareResult === 'copied-image-link') return IMAGE_LINK_COPIED_MESSAGE

  return null
}

export function useCommunityMemoExternalShare() {
  const [isSharing, setIsSharing] = useState(false)

  const shareCommunityMemo = useCallback(async (detail: CommunityMemoDetailResponse | null) => {
    if (!detail || isSharing) return

    setIsSharing(true)
    try {
      const shareInfo = await postCommunityMemoShare(detail.memoUuid)
      const title = `${detail.authorNickname || DEFAULT_AUTHOR_NICKNAME}\uc758 \uba54\ubaa8`
      const shareResult = await shareExternalImage({
        title,
        text: SHARE_TEXT,
        imageUrl: shareInfo.imageUrl,
        fileNameBase: 'community-memo-qr',
      })
      const successMessage = getShareSuccessMessage(shareResult)
      if (successMessage) toast.success(successMessage)
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return

      toast.error(toErrorMessage(error))
    } finally {
      setIsSharing(false)
    }
  }, [isSharing])

  return {
    isSharing,
    shareCommunityMemo,
  }
}
