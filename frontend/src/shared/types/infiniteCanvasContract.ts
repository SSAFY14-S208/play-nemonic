import type {
  InfiniteCanvasWsEventPayloadMap,
  InfiniteCanvasWsEventType,
} from './infiniteCanvas'

export const INFINITE_CANVAS_WS_EVENT_TYPES = [
  'STATE_SNAPSHOT',
  'SNAPSHOT_UPDATED',
  'OPS_APPLIED',
  'CURSOR_UPDATED',
  'LOCK_ACQUIRED',
  'LOCK_RELEASED',
  'PARTICIPANT_CONNECTED',
  'PARTICIPANT_DISCONNECTED',
  'PARTICIPANT_LEFT',
  'HOST_CHANGED',
  'PARTICIPANT_UPDATED',
  'CANVAS_CLOSED',
  'DUPLICATE_SESSION_CLOSED',
  'PONG',
  'ERROR',
] as const satisfies readonly InfiniteCanvasWsEventType[]

export const INFINITE_CANVAS_WS_EVENT_PAYLOAD_KIND = {
  STATE_SNAPSHOT: 'state',
  SNAPSHOT_UPDATED: 'state',
  OPS_APPLIED: 'operations',
  CURSOR_UPDATED: 'cursor',
  LOCK_ACQUIRED: 'lock',
  LOCK_RELEASED: 'lock',
  PARTICIPANT_CONNECTED: 'state',
  PARTICIPANT_DISCONNECTED: 'state',
  PARTICIPANT_LEFT: 'leave',
  HOST_CHANGED: 'leave',
  PARTICIPANT_UPDATED: 'participant',
  CANVAS_CLOSED: 'message-or-closed-at',
  DUPLICATE_SESSION_CLOSED: 'message',
  PONG: 'message',
  ERROR: 'message',
} as const satisfies Record<InfiniteCanvasWsEventType, string>

type InfiniteCanvasWsEventTypeListValue = (typeof INFINITE_CANVAS_WS_EVENT_TYPES)[number]

type ExactInfiniteCanvasWsEventTypeList =
  Exclude<InfiniteCanvasWsEventType, InfiniteCanvasWsEventTypeListValue> extends never
    ? Exclude<InfiniteCanvasWsEventTypeListValue, InfiniteCanvasWsEventType> extends never
      ? true
      : never
    : never

type ExactInfiniteCanvasWsPayloadMap =
  Exclude<InfiniteCanvasWsEventType, keyof InfiniteCanvasWsEventPayloadMap> extends never
    ? Exclude<keyof InfiniteCanvasWsEventPayloadMap, InfiniteCanvasWsEventType> extends never
      ? true
      : never
    : never

export const INFINITE_CANVAS_WS_EVENT_TYPE_LIST_CHECK: ExactInfiniteCanvasWsEventTypeList =
  true

export const INFINITE_CANVAS_WS_PAYLOAD_MAP_CHECK: ExactInfiniteCanvasWsPayloadMap = true

export const isInfiniteCanvasWsEventType = (
  value: string,
): value is InfiniteCanvasWsEventType =>
  INFINITE_CANVAS_WS_EVENT_TYPES.some((eventType) => eventType === value)
