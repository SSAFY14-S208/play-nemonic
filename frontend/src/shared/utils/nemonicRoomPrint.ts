export type NemonicRoomPrintSourceKind = 'GALLERY' | 'PHONE_DRAWING'

export interface NemonicRoomPrintDraft {
  sourceKind: NemonicRoomPrintSourceKind
  title: string
  imageUrl: string
  thumbnailUrl?: string | null
  sourceGalleryId?: string | null
  sourceContentKind?: string | null
  createdAt?: string
}

const NEMONIC_ROOM_PRINT_KEY = 'nemonic-room-print-draft'

export const NEMONIC_ROOM_PRINT_EVENT = 'nemonic-room-print-ready'

function normalizeNemonicRoomPrintDraft(
  value: unknown,
): NemonicRoomPrintDraft | null {
  if (!value || typeof value !== 'object') {
    return null
  }

  const candidate = value as Partial<NemonicRoomPrintDraft>

  if (
    candidate.sourceKind !== 'GALLERY' &&
    candidate.sourceKind !== 'PHONE_DRAWING'
  ) {
    return null
  }

  if (typeof candidate.imageUrl !== 'string' || candidate.imageUrl.length === 0) {
    return null
  }

  return {
    sourceKind: candidate.sourceKind,
    title:
      typeof candidate.title === 'string' && candidate.title.length > 0
        ? candidate.title
        : '네모닉 출력물',
    imageUrl: candidate.imageUrl,
    thumbnailUrl:
      typeof candidate.thumbnailUrl === 'string' ? candidate.thumbnailUrl : null,
    sourceGalleryId:
      typeof candidate.sourceGalleryId === 'string'
        ? candidate.sourceGalleryId
        : null,
    sourceContentKind:
      typeof candidate.sourceContentKind === 'string'
        ? candidate.sourceContentKind
        : null,
    createdAt:
      typeof candidate.createdAt === 'string' ? candidate.createdAt : undefined,
  }
}

export function writeNemonicRoomPrintDraft(
  draft: Omit<NemonicRoomPrintDraft, 'createdAt'>,
) {
  if (typeof window === 'undefined') {
    return false
  }

  const nextDraft: NemonicRoomPrintDraft = {
    ...draft,
    createdAt: new Date().toISOString(),
  }

  window.sessionStorage.setItem(
    NEMONIC_ROOM_PRINT_KEY,
    JSON.stringify(nextDraft),
  )
  window.dispatchEvent(new CustomEvent(NEMONIC_ROOM_PRINT_EVENT))
  return true
}

export function consumeNemonicRoomPrintDraft() {
  if (typeof window === 'undefined') {
    return null
  }

  const rawDraft = window.sessionStorage.getItem(NEMONIC_ROOM_PRINT_KEY)
  window.sessionStorage.removeItem(NEMONIC_ROOM_PRINT_KEY)

  if (!rawDraft) {
    return null
  }

  try {
    return normalizeNemonicRoomPrintDraft(JSON.parse(rawDraft))
  } catch {
    return null
  }
}
