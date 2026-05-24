import { parseServerInstant } from '@/shared/utils'
import type { GalleryItemResponse } from '@/shared/types'
import type { PhoneGalleryItem, PhoneGalleryItemKind } from '..'

const KNOWN_KINDS: PhoneGalleryItemKind[] = [
  'phone',
  'fortune',
  'flipbook',
  'relay',
  'infinite',
]

// 백엔드(GalleryRepository SQL)가 보내는 kind 문자열 → 프론트 union 매핑.
// 백엔드 값 출처: backend/src/main/java/.../gallery/repository/GalleryRepository.java
const SERVER_KIND_TO_PHONE_KIND: Record<string, PhoneGalleryItemKind> = {
  phone: 'phone',
  fortune: 'fortune',
  flipbook: 'flipbook',
  flipbook_gif: 'flipbook',
  relay_drawing: 'relay',
  infinite_canvas: 'infinite',
  community_memo: 'infinite',
}

function normalizeKind(rawKind: string): PhoneGalleryItemKind {
  const lower = rawKind.toLowerCase()
  const mapped = SERVER_KIND_TO_PHONE_KIND[lower]
  if (mapped) return mapped
  if (KNOWN_KINDS.includes(lower as PhoneGalleryItemKind)) {
    return lower as PhoneGalleryItemKind
  }
  return 'phone'
}

const DEFAULT_TITLE_BY_KIND: Record<PhoneGalleryItemKind, string> = {
  phone: '내가 그린 메모',
  fortune: '오늘의 운세',
  flipbook: '플립북',
  relay: '릴레이',
  infinite: '커뮤니티 캔버스',
}

const MINUTE_MS = 60 * 1000
const HOUR_MS = 60 * MINUTE_MS
const DAY_MS = 24 * HOUR_MS

export function formatRelativeTime(isoString: string, now: Date = new Date()): string {
  // 백엔드가 LocalDateTime(zone suffix 없음)을 UTC로 보내므로 Z를 붙여 파싱한다.
  const created = parseServerInstant(isoString)
  const diff = now.getTime() - created.getTime()
  if (Number.isNaN(diff)) return ''
  if (diff < MINUTE_MS) return '방금 전'
  if (diff < HOUR_MS) return `${Math.floor(diff / MINUTE_MS)}분 전`
  if (diff < DAY_MS) return `${Math.floor(diff / HOUR_MS)}시간 전`
  if (diff < 2 * DAY_MS) return '어제'
  if (diff < 7 * DAY_MS) return `${Math.floor(diff / DAY_MS)}일 전`
  if (diff < 30 * DAY_MS) return `${Math.floor(diff / (7 * DAY_MS))}주 전`
  if (diff < 365 * DAY_MS) return `${Math.floor(diff / (30 * DAY_MS))}달 전`
  return `${Math.floor(diff / (365 * DAY_MS))}년 전`
}

export function mapGalleryItemResponseToPhoneItem(
  item: GalleryItemResponse,
): PhoneGalleryItem {
  const kind = normalizeKind(item.kind)
  return {
    id: item.galleryId,
    artifactId: item.artifactId,
    kind,
    title: DEFAULT_TITLE_BY_KIND[kind],
    createdAtLabel: formatRelativeTime(item.createdAt),
    imageDataUrl: item.thumbnailUrl || undefined,
  }
}
