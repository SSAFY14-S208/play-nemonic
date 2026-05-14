import { create } from 'zustand'
import type { HubFocusKey } from '@/shared/types'
import { trackHubStoreUpdate } from '@/shared/utils'

interface HubRoomStore {
  focusKey: HubFocusKey
  setFocus: (focusKey: HubFocusKey) => void
}

export const useHubRoomStore = create<HubRoomStore>((set) => ({
  focusKey: 'overview',
  setFocus: (focusKey) => {
    trackHubStoreUpdate('hubRoomStore', 'setFocus')
    set({ focusKey })
  },
}))
