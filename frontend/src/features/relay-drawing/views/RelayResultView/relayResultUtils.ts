import { ApiError } from '@/shared/apis'
import type { RelayPart, RelayRoomResultItemResponse } from '@/shared/types'
import type { ExternalImageShareResult } from '@/shared/utils'

import {
  RELAY_ROUND_RULES,
  SEGMENT_TAG_CLASSNAMES,
  type RelayResultSegment,
  type RelayRoundKey,
} from '@/features/relay-drawing/constants'

export const RELAY_EXTERNAL_SHARE_TEXT = '함께 완성한 릴레이 드로잉 결과를 공유합니다.'
export const RELAY_SHARE_IMAGE_COPIED_MESSAGE =
  '릴레이 드로잉 QR 공유 이미지를 클립보드에 복사했습니다. 원하는 곳에 붙여넣어 공유할 수 있습니다.'
export const RELAY_SHARE_IMAGE_LINK_COPIED_MESSAGE =
  '릴레이 드로잉 QR 공유 이미지 링크를 클립보드에 복사했습니다.'
export const RELAY_SHARE_GIF_LINK_COPIED_MESSAGE = '릴레이 드로잉 QR GIF 공유 링크를 클립보드에 복사했습니다.'

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
    : `릴레이 드로잉 결과 ${canvasIndex + 1}`
}

export function getActiveResultTitle(activeResultItem: RelayRoomResultItemResponse | null) {
  if (!activeResultItem) return '릴레이 드로잉 결과'

  const faceDrawerNickname =
    activeResultItem.parts.find((partItem) => partItem.part === 'FACE')?.drawerNickname ?? null

  return getResultTitle({
    faceDrawerNickname,
    canvasIndex: activeResultItem.canvasIndex,
  })
}

export function getResultSegments({
  activeResultItem,
  currentUserUuid,
}: {
  activeResultItem: RelayRoomResultItemResponse | null
  currentUserUuid: string | null
}) {
  if (!activeResultItem) {
    return {
      segments: [] as RelayResultSegment[],
      resultImageUrl: null as string | null,
    }
  }

  const segments: RelayResultSegment[] = activeResultItem.parts.map((partItem) => {
    const roundKey = partToRoundKey(partItem.part)
    const roundRule = RELAY_ROUND_RULES[roundKey]
    const isMe = partItem.drawerUserUuid === currentUserUuid
    const displayName = isMe ? `${partItem.drawerNickname} (나)` : partItem.drawerNickname

    return {
      key: roundKey,
      participantName: displayName,
      roleLabel: roundRule.label,
      tagLabel: `${partItem.drawerNickname} · ${roundRule.label}`,
      tagClassName: SEGMENT_TAG_CLASSNAMES[roundKey],
    }
  })

  return {
    segments,
    resultImageUrl: activeResultItem.contentUrl ?? null,
  }
}

export function getExternalShareSuccessMessage(shareResult: ExternalImageShareResult) {
  if (shareResult === 'copied-gif-link') return RELAY_SHARE_GIF_LINK_COPIED_MESSAGE
  if (shareResult === 'copied-image') return RELAY_SHARE_IMAGE_COPIED_MESSAGE
  if (shareResult === 'copied-image-link') return RELAY_SHARE_IMAGE_LINK_COPIED_MESSAGE

  return null
}

export function toExternalShareErrorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message || '공유 정보를 가져오지 못했습니다.'
  if (error instanceof Error) {
    if (error.message === 'file-share-unavailable') {
      return '이 브라우저에서는 파일 공유를 사용할 수 없습니다.'
    }

    return error.message || '결과 공유 중 오류가 발생했습니다.'
  }

  return '결과 공유 중 오류가 발생했습니다.'
}
