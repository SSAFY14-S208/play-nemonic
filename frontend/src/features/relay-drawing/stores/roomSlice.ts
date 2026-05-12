import type { StateCreator } from 'zustand'

import {
  DEFAULT_DRAWING_STROKE_WIDTH,
  DRAWING_COLORS,
} from '@/shared/constants'
import { DEFAULT_TIME_LIMIT_SECONDS } from '../constants'

import type { RelayDrawingStore, RoomSlice } from './store.types'

// 정원 기본값 — 백엔드 hydrate 전에는 가이드 §9의 기본 정원으로 표시한다.
const DEFAULT_MIN_PARTICIPANTS = 2
const DEFAULT_MAX_PARTICIPANTS = 6

export const createRoomSlice: StateCreator<RelayDrawingStore, [], [], RoomSlice> = (
  set,
) => ({
  roomCode: null,
  roomStatus: null,
  hostUserUuid: null,
  participants: [],
  timeLimitSeconds: DEFAULT_TIME_LIMIT_SECONDS,
  minParticipants: DEFAULT_MIN_PARTICIPANTS,
  maxParticipants: DEFAULT_MAX_PARTICIPANTS,
  dismissalReason: null,

  hydrateRoomState: (payload) => {
    set({
      roomCode: payload.roomCode,
      roomStatus: payload.status,
      hostUserUuid: payload.hostUserUuid,
      participants: payload.participants,
      timeLimitSeconds: payload.timeLimitSeconds,
      minParticipants: payload.minParticipants,
      maxParticipants: payload.maxParticipants,
    })
  },

  setRoomStatus: (roomStatus) => set({ roomStatus }),
  setParticipants: (participants) => set({ participants }),
  setHostUserUuid: (hostUserUuid) => set({ hostUserUuid }),
  setTimeLimitSeconds: (seconds) => set({ timeLimitSeconds: seconds }),
  setDismissalReason: (reason) => set({ dismissalReason: reason }),

  // clearRoom은 룸 슬라이스가 주체지만, 다음 룸 진입이 stale state로 시작하지
  // 않도록 캔버스/결과 슬라이스도 함께 비워준다. set()이 shallow merge라서
  // 슬라이스 경계를 넘는 필드 갱신이 자연스럽게 동작한다. 정원은 다음 hydrate
  // 전 fallback이 필요하므로 기본값으로 되돌린다.
  clearRoom: () => {
    set({
      roomCode: null,
      roomStatus: null,
      hostUserUuid: null,
      participants: [],
      minParticipants: DEFAULT_MIN_PARTICIPANTS,
      maxParticipants: DEFAULT_MAX_PARTICIPANTS,
      dismissalReason: null,
      // 캔버스 슬라이스 리셋
      activeRoundKey: 'face',
      selectedToolKey: 'pencil',
      selectedColor: DRAWING_COLORS[0],
      selectedOpacity: 1,
      strokeWidth: DEFAULT_DRAWING_STROKE_WIDTH,
      recentColors: [],
      roundLines: { face: [], body: [], legs: [] },
      roundRedoStack: { face: [], body: [], legs: [] },
      canvasIndex: null,
      currentPart: null,
      partDeadlineAt: null,
      hintImageUrl: null,
      isSubmitting: false,
      isSubmitted: false,
      submittedCount: 0,
      totalCount: 0,
      isTransitioning: false,
      isPartTimeUp: false,
      partFetchTrigger: 0,
      pendingAutoSubmitTrigger: 0,
      // 결과 슬라이스 리셋
      completedAt: null,
      resultRevealStep: 'final',
      resultItems: [],
      activeResultIndex: 0,
    })
  },
})
