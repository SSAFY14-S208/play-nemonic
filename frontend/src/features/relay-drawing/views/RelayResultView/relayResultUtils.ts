import { ApiError } from '@/shared/apis'
import type { RelayPart } from '@/shared/types'
import type { ExternalImageShareResult } from '@/shared/utils'

import type { RelayRoundKey } from '@/features/relay-drawing/constants'

// ── 상수 ──────────────────────────────────────────────────────────────

export const RELAY_EXTERNAL_SHARE_TEXT = '네모닉 릴레이 드로잉 결과를 공유해요.'
export const RELAY_SHARE_IMAGE_COPIED_MESSAGE =
  '릴레이 드로잉 QR 공유 이미지를 복사했어요. 채팅창에 붙여 넣어 주세요.'
export const RELAY_SHARE_IMAGE_LINK_COPIED_MESSAGE =
  '릴레이 드로잉 QR 공유 이미지 링크를 복사했어요.'
export const RELAY_SHARE_GIF_LINK_COPIED_MESSAGE = '릴레이 드로잉 QR GIF 공유 링크를 복사했어요.'

// ── 순수 함수 ──────────────────────────────────────────────────────────

export function partToRoundKey(part: RelayPart): RelayRoundKey {
  return part.toLowerCase() as RelayRoundKey
}

export function isRealArtifactId(artifactId: string | null | undefined) {
  return Boolean(artifactId)
}

export function getResultTitle({
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

export function getExternalShareSuccessMessage(shareResult: ExternalImageShareResult) {
  if (shareResult === 'copied-gif-link') return RELAY_SHARE_GIF_LINK_COPIED_MESSAGE
  if (shareResult === 'copied-image') return RELAY_SHARE_IMAGE_COPIED_MESSAGE
  if (shareResult === 'copied-image-link') return RELAY_SHARE_IMAGE_LINK_COPIED_MESSAGE

  return null
}

export function toExternalShareErrorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message || '외부 공유 정보를 만들 수 없어요.'
  if (error instanceof Error) {
    if (error.message === 'file-share-unavailable') {
      return '이 브라우저에서는 이미지 파일 공유를 사용할 수 없어요.'
    }

    return error.message || '외부 공유 정보를 만들 수 없어요.'
  }

  return '외부 공유 정보를 만들 수 없어요.'
}
