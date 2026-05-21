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

  try {
    const snapshot = JSON.parse(rawSnapshot) as Partial<InfiniteCanvasCreateResponse> & {
      ownerUserUuid?: unknown
    }
    const hostUserUuid =
      typeof snapshot.hostUserUuid === 'string'
        ? snapshot.hostUserUuid
        : typeof snapshot.ownerUserUuid === 'string'
          ? snapshot.ownerUserUuid
          : null

    if (
      typeof snapshot.roomCode !== 'string' ||
      snapshot.roomCode !== roomCode ||
      hostUserUuid === null ||
      !Array.isArray(snapshot.participants)
    ) {
      return null
    }

    const participants = (snapshot.participants as InfiniteCanvasParticipantResponse[]).map(
      (participant) => ({
        ...participant,
        host: participant.userUuid === hostUserUuid,
      }),
    )

    return {
      roomCode: snapshot.roomCode,
      status: snapshot.status ?? 'ACTIVE',
      hostUserUuid,
      maxParticipants: Number(snapshot.maxParticipants) || 0,
      participantCount: Number(snapshot.participantCount) || participants.length,
      participants,
      createdAt: snapshot.createdAt ?? new Date().toISOString(),
    } satisfies InfiniteCanvasCreateResponse
  } catch {
    return null
  }
}
