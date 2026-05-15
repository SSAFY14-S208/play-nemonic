export type CommunityCanvasHandoffSourceKind = 'GALLERY' | 'FORTUNE' | 'RELAY' | 'FLIPBOOK'

export interface CommunityCanvasHandoffDraft {
  sourceKind: CommunityCanvasHandoffSourceKind
  title: string
  imageUrl: string
  thumbnailUrl?: string | null
  sourceGalleryId?: string | null
  sourceContentKind?: string | null
  createdAt?: string
}

const COMMUNITY_CANVAS_HANDOFF_KEY = 'nemonic-community-canvas-handoff'

function normalizeCommunityCanvasHandoffDraft(
  value: unknown,
): CommunityCanvasHandoffDraft | null {
  if (!value || typeof value !== 'object') {
    return null
  }

  const candidate = value as Partial<CommunityCanvasHandoffDraft>

  if (
    candidate.sourceKind !== 'GALLERY' &&
    candidate.sourceKind !== 'FORTUNE' &&
    candidate.sourceKind !== 'RELAY' &&
    candidate.sourceKind !== 'FLIPBOOK'
  ) {
    return null
  }

  if (typeof candidate.imageUrl !== 'string' || candidate.imageUrl.length === 0) {
    return null
  }

  return {
    sourceKind: candidate.sourceKind,
    title: typeof candidate.title === 'string' && candidate.title.length > 0
      ? candidate.title
      : '커뮤니티 메모',
    imageUrl: candidate.imageUrl,
    thumbnailUrl:
      typeof candidate.thumbnailUrl === 'string' ? candidate.thumbnailUrl : null,
    sourceGalleryId:
      typeof candidate.sourceGalleryId === 'string' ? candidate.sourceGalleryId : null,
    sourceContentKind:
      typeof candidate.sourceContentKind === 'string' ? candidate.sourceContentKind : null,
    createdAt:
      typeof candidate.createdAt === 'string' ? candidate.createdAt : undefined,
  }
}

export function writeCommunityCanvasHandoffDraft(
  draft: Omit<CommunityCanvasHandoffDraft, 'createdAt'>,
) {
  if (typeof window === 'undefined') {
    return false
  }

  const nextDraft: CommunityCanvasHandoffDraft = {
    ...draft,
    createdAt: new Date().toISOString(),
  }

  window.sessionStorage.setItem(
    COMMUNITY_CANVAS_HANDOFF_KEY,
    JSON.stringify(nextDraft),
  )
  return true
}

export function consumeCommunityCanvasHandoffDraft() {
  if (typeof window === 'undefined') {
    return null
  }

  const rawDraft = window.sessionStorage.getItem(COMMUNITY_CANVAS_HANDOFF_KEY)
  window.sessionStorage.removeItem(COMMUNITY_CANVAS_HANDOFF_KEY)

  if (!rawDraft) {
    return null
  }

  try {
    return normalizeCommunityCanvasHandoffDraft(JSON.parse(rawDraft))
  } catch {
    return null
  }
}
