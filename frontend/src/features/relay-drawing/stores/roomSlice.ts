import type { StateCreator } from 'zustand'

import { DEFAULT_TIME_LIMIT_SECONDS } from '../constants'

import type { RelayDrawingStore, RoomSlice } from './store.types'

export const createRoomSlice: StateCreator<RelayDrawingStore, [], [], RoomSlice> = (
  set,
) => ({
  roomCode: null,
  roomStatus: null,
  hostUserUuid: null,
  participants: [],
  timeLimitSeconds: DEFAULT_TIME_LIMIT_SECONDS,

  hydrateRoomState: (payload) => {
    set({
      roomCode: payload.roomCode,
      roomStatus: payload.status,
      hostUserUuid: payload.hostUserUuid,
      participants: payload.participants,
      timeLimitSeconds: payload.timeLimitSeconds,
    })
  },

  setRoomStatus: (roomStatus) => set({ roomStatus }),
  setParticipants: (participants) => set({ participants }),
  setHostUserUuid: (hostUserUuid) => set({ hostUserUuid }),
  setTimeLimitSeconds: (seconds) => set({ timeLimitSeconds: seconds }),

  // clearRoom은 룸 슬라이스가 주체지만, 다음 룸 진입이 stale state로 시작하지
  // 않도록 캔버스/결과 슬라이스도 함께 비워준다. set()이 shallow merge라서
  // 슬라이스 경계를 넘는 필드 갱신이 자연스럽게 동작한다.
  clearRoom: () => {
    set({
      roomCode: null,
      roomStatus: null,
      hostUserUuid: null,
      participants: [],
      activeRoundKey: 'face',
      roundLines: { face: [], body: [], legs: [] },
      completedAt: null,
      resultRevealStep: 'final',
    })
  },
})
