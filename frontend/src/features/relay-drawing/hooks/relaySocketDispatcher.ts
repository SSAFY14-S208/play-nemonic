import type { RelayWsEvent } from '@/shared/types'

import type { RelayEventHandlers } from './relaySocketTypes'

export function dispatchRelaySocketEvent(
  handlers: RelayEventHandlers,
  event: RelayWsEvent,
) {
  switch (event.type) {
    case 'PARTICIPANT_CONNECTED':
      handlers.PARTICIPANT_CONNECTED?.(event)
      break
    case 'PARTICIPANT_DISCONNECTED':
      handlers.PARTICIPANT_DISCONNECTED?.(event)
      break
    case 'PARTICIPANT_LEFT':
      handlers.PARTICIPANT_LEFT?.(event)
      break
    case 'PARTICIPANT_DROPPED':
      handlers.PARTICIPANT_DROPPED?.(event)
      break
    case 'SETTINGS_CHANGED':
      handlers.SETTINGS_CHANGED?.(event)
      break
    case 'GAME_STARTED':
      handlers.GAME_STARTED?.(event)
      break
    case 'PART_STARTED':
      handlers.PART_STARTED?.(event)
      break
    case 'PART_SUBMITTED':
      handlers.PART_SUBMITTED?.(event)
      break
    case 'PART_AUTO_SUBMITTED':
      handlers.PART_AUTO_SUBMITTED?.(event)
      break
    case 'PART_TIME_UP':
      handlers.PART_TIME_UP?.(event)
      break
    case 'ALL_PARTS_COMPLETED':
      handlers.ALL_PARTS_COMPLETED?.(event)
      break
    case 'RESULT_CREATED':
      handlers.RESULT_CREATED?.(event)
      break
    case 'HOST_CHANGED':
      handlers.HOST_CHANGED?.(event)
      break
    case 'ROOM_CLOSED':
      handlers.ROOM_CLOSED?.(event)
      break
    case 'PARTICIPANT_KICKED':
      handlers.PARTICIPANT_KICKED?.(event)
      break
    case 'KICKED_FROM_ROOM':
      handlers.KICKED_FROM_ROOM?.(event)
      break
    case 'DUPLICATE_SESSION_CLOSED':
      handlers.DUPLICATE_SESSION_CLOSED?.(event)
      break
  }
}
