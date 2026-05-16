import type {
  InfiniteCanvasCreateResponse,
  InfiniteCanvasParticipantResponse,
  InviteJoinResponse,
} from '@/shared/types'

const INFINITE_CANVAS_ROOM_BASE_PATH = '/infinite-canvas'
const INFINITE_CANVAS_BOOTH_TYPES = new Set(['infinite_canvas', 'infinite-canvas'])
const INFINITE_CANVAS_CREATED_ROOM_STORAGE_PREFIX = 'infinite-canvas:created-room:'

export function buildInfiniteCanvasRoomPath(roomCode: string) {
  return `${INFINITE_CANVAS_ROOM_BASE_PATH}/${encodeURIComponent(roomCode)}`
}

export function normalizeInfiniteCanvasInviteCode(inviteCode: string) {
  return inviteCode.trim().toUpperCase()
}

export function isInfiniteCanvasBoothType(boothType: string) {
  return INFINITE_CANVAS_BOOTH_TYPES.has(boothType.trim().toLowerCase())
}

export function resolveInfiniteCanvasInviteRoomPath(
  invite: Pick<InviteJoinResponse, 'boothType' | 'roomId'>,
) {
  if (!isInfiniteCanvasBoothType(invite.boothType)) return null

  return buildInfiniteCanvasRoomPath(invite.roomId)
}

export function saveInfiniteCanvasCreatedRoomSnapshot(snapshot: InfiniteCanvasCreateResponse) {
  if (typeof window === 'undefined') return

  window.sessionStorage.setItem(
    `${INFINITE_CANVAS_CREATED_ROOM_STORAGE_PREFIX}${snapshot.roomCode}`,
    JSON.stringify(snapshot),
  )
}

export function takeInfiniteCanvasCreatedRoomSnapshot(roomCode: string) {
  if (typeof window === 'undefined') return null

  const storageKey = `${INFINITE_CANVAS_CREATED_ROOM_STORAGE_PREFIX}${roomCode}`
  const rawSnapshot = window.sessionStorage.getItem(storageKey)
  if (!rawSnapshot) return null

  window.sessionStorage.removeItem(storageKey)

  try {
    const snapshot = JSON.parse(rawSnapshot) as Partial<InfiniteCanvasCreateResponse>
    if (
      typeof snapshot.roomCode !== 'string' ||
      snapshot.roomCode !== roomCode ||
      typeof snapshot.ownerUserUuid !== 'string' ||
      !Array.isArray(snapshot.participants)
    ) {
      return null
    }

    return {
      roomCode: snapshot.roomCode,
      status: snapshot.status ?? 'ACTIVE',
      ownerUserUuid: snapshot.ownerUserUuid,
      maxParticipants: Number(snapshot.maxParticipants) || snapshot.participants.length,
      participantCount: Number(snapshot.participantCount) || snapshot.participants.length,
      participants: snapshot.participants as InfiniteCanvasParticipantResponse[],
      createdAt: snapshot.createdAt ?? new Date().toISOString(),
    } satisfies InfiniteCanvasCreateResponse
  } catch {
    return null
  }
}
