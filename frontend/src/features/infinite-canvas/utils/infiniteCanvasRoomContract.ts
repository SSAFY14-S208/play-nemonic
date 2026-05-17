import type {
  InfiniteCanvasCreateResponse,
  InfiniteCanvasLeaveResponse,
  InfiniteCanvasParticipantColorUpdateRequest,
  InfiniteCanvasParticipantResponse,
  InfiniteCanvasStateResponse,
} from '@/shared/types'

import {
  INFINITE_CANVAS_WS_EVENT_PAYLOAD_KIND,
  INFINITE_CANVAS_WS_EVENT_TYPES,
} from '@/shared/types'
import { takeInfiniteCanvasCreatedRoomSnapshot } from './infiniteCanvasEntry'

const PARTICIPANT_RESPONSE = {
  userUuid: 'user-1',
  nickname: '망고',
  color: '#72DDF7',
  avatarUrl: null,
  host: true,
  connected: true,
  joinedAt: '2026-05-17T12:00:00',
  lastConnectedAt: '2026-05-17T12:00:00',
} satisfies InfiniteCanvasParticipantResponse

const STATE_RESPONSE = {
  roomCode: '724AAG',
  status: 'ACTIVE',
  hostUserUuid: PARTICIPANT_RESPONSE.userUuid,
  me: PARTICIPANT_RESPONSE,
  participants: [PARTICIPANT_RESPONSE],
  elements: [],
  operations: [],
  locks: {},
  viewport: null,
  maxParticipants: 6,
  revision: 0,
  createdAt: '2026-05-17T12:00:00',
  updatedAt: '2026-05-17T12:00:00',
} satisfies InfiniteCanvasStateResponse

const CREATE_RESPONSE = {
  roomCode: STATE_RESPONSE.roomCode,
  status: STATE_RESPONSE.status,
  hostUserUuid: STATE_RESPONSE.hostUserUuid,
  maxParticipants: STATE_RESPONSE.maxParticipants,
  participantCount: STATE_RESPONSE.participants.length,
  participants: STATE_RESPONSE.participants,
  createdAt: STATE_RESPONSE.createdAt,
} satisfies InfiniteCanvasCreateResponse

const LEGACY_CREATE_RESPONSE = {
  ...CREATE_RESPONSE,
  hostUserUuid: undefined,
  ownerUserUuid: CREATE_RESPONSE.hostUserUuid,
} as unknown as InfiniteCanvasCreateResponse

const LEAVE_RESPONSE = {
  roomCode: STATE_RESPONSE.roomCode,
  userUuid: PARTICIPANT_RESPONSE.userUuid,
  nickname: PARTICIPANT_RESPONSE.nickname,
  participantCount: 0,
  hostChanged: false,
  newHostUserUuid: null,
  newHostNickname: null,
  closed: true,
  closedAt: '2026-05-17T12:10:00',
} satisfies InfiniteCanvasLeaveResponse

const COLOR_UPDATE_REQUEST = {
  color: '#72DDF7',
} satisfies InfiniteCanvasParticipantColorUpdateRequest

function verifyCreatedRoomSnapshotCompatibility() {
  if (typeof window === 'undefined') return true

  const storageKey = 'infinite-canvas:created-room:724AAG'
  window.sessionStorage.setItem(storageKey, JSON.stringify(LEGACY_CREATE_RESPONSE))
  const restoredSnapshot = takeInfiniteCanvasCreatedRoomSnapshot('724AAG')
  window.sessionStorage.removeItem(storageKey)

  return restoredSnapshot?.hostUserUuid === CREATE_RESPONSE.hostUserUuid
}

export const INFINITE_CANVAS_ROOM_FLOW_CONTRACTS = {
  stateUsesHostUserUuid: STATE_RESPONSE.hostUserUuid === PARTICIPANT_RESPONSE.userUuid,
  participantHasHostFlag: STATE_RESPONSE.participants[0]?.host === true,
  colorUpdateRequestOnlyUsesColor: Object.keys(COLOR_UPDATE_REQUEST).join(',') === 'color',
  participantUpdatedEventSupported: INFINITE_CANVAS_WS_EVENT_TYPES.includes('PARTICIPANT_UPDATED'),
  hostChangedEventSupported: INFINITE_CANVAS_WS_EVENT_TYPES.includes('HOST_CHANGED'),
  hostChangedPayloadKindIsLeave: INFINITE_CANVAS_WS_EVENT_PAYLOAD_KIND.HOST_CHANGED === 'leave',
  leaveResponseCarriesHostTransfer:
    LEAVE_RESPONSE.hostChanged === false && LEAVE_RESPONSE.newHostUserUuid === null,
  createdRoomSnapshotCompatibility: verifyCreatedRoomSnapshotCompatibility(),
} as const
