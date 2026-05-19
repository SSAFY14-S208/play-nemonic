import { create } from 'zustand'

import { createCanvasSlice } from './canvasSlice'
import { createResultSlice } from './resultSlice'
import { createRoomSlice } from './roomSlice'
import type { RelayDrawingStore } from './store.types'

export const useRelayDrawingStore = create<RelayDrawingStore>()((...args) => ({
  ...createRoomSlice(...args),
  ...createCanvasSlice(...args),
  ...createResultSlice(...args),
}))

export type {
  CanvasSlice,
  RelayDismissalReason,
  RelayDrawingStore,
  RelayRoomHydratePayload,
  ResultSlice,
  RoomSlice,
} from './store.types'

export { useRelayBgmStore } from './relayBgmStore'
export { useRelayHowToPlayStore } from './relayHowToPlayStore'
