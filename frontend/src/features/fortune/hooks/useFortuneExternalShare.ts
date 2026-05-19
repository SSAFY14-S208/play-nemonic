'use client'

import { useCallback, useState } from 'react'
import { toast } from 'sonner'

import { ApiError, postArtifactShare } from '@/shared/apis'
import { logEvent } from '@/shared/libs'

import { useFortuneSessionStore } from '../fortuneSessionStore'

const FORTUNE_SHARE_TEXT = '네모닉 운세 결과를 공유해요.'

function isServerFortuneId(fortuneId: string | null | undefined) {
  return Boolean(fortuneId && !fortuneId.startsWith('fortune-'))
}

function toExternalShareErrorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message || '외부 공유 정보를 만들 수 없어요.'
  if (error instanceof Error) return error.message || '외부 공유 정보를 만들 수 없어요.'

  return '외부 공유 정보를 만들 수 없어요.'
}

async function copyTextToClipboard(text: string) {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch {
      // Clipboard API 실패 시 fallback으로 진행
    }
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()

  try {
    document.execCommand('copy')
  } finally {
    document.body.removeChild(textarea)
  }
}

function isLikelyMobileEnvironment() {
  if (typeof navigator === 'undefined') return false

  const userAgent = navigator.userAgent.toLowerCase()
  return (
    /android|iphone|ipad|ipod/.test(userAgent) ||
    (navigator.maxTouchPoints > 1 && /macintosh/.test(userAgent))
  )
}

export function useFortuneExternalShare() {
  const result = useFortuneSessionStore((state) => state.result)
  const [isSharingExternal, setIsSharingExternal] = useState(false)
  const canShareExternal = Boolean(result && isServerFortuneId(result.id)) && !isSharingExternal

  const shareExternal = useCallback(async () => {
    if (!result || !isServerFortuneId(result.id) || isSharingExternal) {
      toast.error('외부 공유는 서버에서 생성된 운세에서만 사용할 수 있어요.')
      return
    }

    setIsSharingExternal(true)

    try {
      const shareInfo = await postArtifactShare(result.id)
      const shareUrl = shareInfo.shareUrl?.trim()
      const imageUrl = shareInfo.imageUrl?.trim()

      if (!shareUrl && !imageUrl) {
        throw new Error('외부 공유 링크를 만들지 못했어요.')
      }

      const emitShared = () => {
        logEvent('result_shared', {
          metadata: {
            funnel_name: 'fortune_creation',
            content_type: 'fortune',
            share_method: 'external_share',
            fortune_id: result.id,
          },
        })
      }

      // 모바일: native share 시도 (이미지 파일 또는 URL)
      if (isLikelyMobileEnvironment() && navigator.share) {
        try {
          if (imageUrl) {
            const imageResponse = await fetch(imageUrl)
            if (imageResponse.ok) {
              const blob = await imageResponse.blob()
              const imageFile = new File(
                [blob],
                'fortune-result-qr.png',
                { type: blob.type || 'image/png' },
              )

              if (navigator.canShare?.({ files: [imageFile] })) {
                await navigator.share({
                  title: result.title,
                  text: FORTUNE_SHARE_TEXT,
                  files: [imageFile],
                })
                emitShared()
                return
              }
            }
          }

          // 이미지 공유 불가 시 URL로 native share
          if (shareUrl) {
            await navigator.share({
              title: result.title,
              text: FORTUNE_SHARE_TEXT,
              url: shareUrl,
            })
            emitShared()
            return
          }
        } catch (error) {
          if (error instanceof DOMException && error.name === 'AbortError') return
          // native share 실패 시 클립보드 복사로 진행
        }
      }

      // 데스크탑 또는 native share 실패: 공유 링크를 클립보드에 텍스트로 복사
      const urlToCopy = shareUrl || imageUrl!
      await copyTextToClipboard(urlToCopy)
      toast.success('공유 링크가 복사되었어요.')
      emitShared()
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
