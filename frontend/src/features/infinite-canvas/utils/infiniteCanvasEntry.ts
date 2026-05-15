import type { InviteJoinResponse } from '@/shared/types'

const INFINITE_CANVAS_ROOM_BASE_PATH = '/infinite-canvas'
const INFINITE_CANVAS_BOOTH_TYPES = new Set(['infinite_canvas', 'infinite-canvas'])

export function buildInfiniteCanvasRoomPath(canvasId: string) {
  return `${INFINITE_CANVAS_ROOM_BASE_PATH}/${encodeURIComponent(canvasId)}`
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
