import type { InviteJoinResponse } from '@/shared/types'

import {
  buildInfiniteCanvasRoomPath,
  isInfiniteCanvasBoothType,
  normalizeInfiniteCanvasInviteCode,
  resolveInfiniteCanvasInviteRoomPath,
} from './infiniteCanvasEntry'

const INFINITE_CANVAS_INVITE_RESPONSE = {
  boothType: 'infinite_canvas',
  roomId: 'canvas-123',
  roomName: '무한 캔버스 방',
  hostNickname: '방장',
  currentParticipants: 1,
  maxParticipants: 6,
  yourRole: 'participant',
  alreadyJoined: false,
} satisfies InviteJoinResponse

const RELAY_INVITE_RESPONSE = {
  ...INFINITE_CANVAS_INVITE_RESPONSE,
  boothType: 'relay',
} satisfies InviteJoinResponse

export const INFINITE_CANVAS_ENTRY_FLOW_CONTRACTS = {
  normalizedInviteCode: normalizeInfiniteCanvasInviteCode(' ab12 ') === 'AB12',
  infiniteCanvasBoothType: isInfiniteCanvasBoothType('infinite_canvas'),
  infiniteCanvasKebabBoothType: isInfiniteCanvasBoothType('infinite-canvas'),
  relayBoothTypeRejected: !isInfiniteCanvasBoothType(RELAY_INVITE_RESPONSE.boothType),
  createRoutePath: buildInfiniteCanvasRoomPath('canvas-123') === '/infinite-canvas/canvas-123',
  inviteRoutePath:
    resolveInfiniteCanvasInviteRoomPath(INFINITE_CANVAS_INVITE_RESPONSE) ===
    '/infinite-canvas/canvas-123',
  invalidInviteRoutePath: resolveInfiniteCanvasInviteRoomPath(RELAY_INVITE_RESPONSE) === null,
} as const
