import type { StateCreator } from "zustand";

import {
  DEFAULT_TIME_LIMIT_ALLOWED_SECONDS,
  DEFAULT_TIME_LIMIT_SECONDS,
} from "../constants";
import {
  DEFAULT_DRAWING_STROKE_WIDTH,
  DRAWING_COLORS,
} from "@/shared/constants";

import type { RelayDrawingStore, RoomSlice } from "./store.types";

// 정원 기본값 — 백엔드 hydrate 전에는 가이드 §9의 기본 정원으로 표시한다.
const DEFAULT_MIN_PARTICIPANTS = 2;
const DEFAULT_MAX_PARTICIPANTS = 6;

export const createRoomSlice: StateCreator<
  RelayDrawingStore,
  [],
  [],
  RoomSlice
> = (set, get) => ({
  roomCode: null,
  roomStatus: null,
  hostUserUuid: null,
  participants: [],
  timeLimitSeconds: DEFAULT_TIME_LIMIT_SECONDS,
  timeLimitAllowedSeconds: DEFAULT_TIME_LIMIT_ALLOWED_SECONDS,
  minParticipants: DEFAULT_MIN_PARTICIPANTS,
  maxParticipants: DEFAULT_MAX_PARTICIPANTS,
  dismissalReason: null,
  gameStartPhase: 'idle' as const,

  hydrateRoomState: (payload) => {
    // 새 roomCode로 hydrate되는 경우 이전 방의 게임 진행 상태를 강제 청소.
    // clearRoom 호출이 누락되는 경로(부스 createRoom/joinRoom, 공유 URL 직접 진입)
    // 에서도 roundSubmitted 같은 가드가 stale로 살아남아 자동제출이 막히는 것을 방지.
    const isDifferentRoom = get().roomCode !== payload.roomCode;
    set({
      roomCode: payload.roomCode,
      roomStatus: payload.status,
      hostUserUuid: payload.hostUserUuid,
      participants: payload.participants,
      timeLimitSeconds: payload.timeLimitSeconds,
      minParticipants: payload.minParticipants,
      maxParticipants: payload.maxParticipants,
      // timeLimitAllowedSeconds는 REST 응답에만 포함되고 WS 이벤트에는 없으므로
      // 존재할 때만 갱신한다.
      ...(payload.timeLimitAllowedSeconds && {
        timeLimitAllowedSeconds: payload.timeLimitAllowedSeconds,
      }),
      ...(isDifferentRoom && {
        activeRoundKey: "face" as const,
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
        submittedUserUuids: [],
        roundSubmitted: { face: false, body: false, legs: false },
        roundDeadlines: { face: null, body: null, legs: null },
        isPartTimeUp: false,
        isTransitioning: false,
        partFetchTrigger: 0,
        pendingAutoSubmitTrigger: 0,
        completedAt: null,
        resultItems: [],
        activeResultIndex: 0,
      }),
    });
  },

  setRoomStatus: (roomStatus) => set({ roomStatus }),
  setParticipants: (participants) => set({ participants }),
  setHostUserUuid: (hostUserUuid) => set({ hostUserUuid }),
  setTimeLimitSeconds: (seconds) => set({ timeLimitSeconds: seconds }),
  setDismissalReason: (reason) => set({ dismissalReason: reason }),
  setGameStartPhase: (phase) => set({ gameStartPhase: phase }),

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
      timeLimitAllowedSeconds: DEFAULT_TIME_LIMIT_ALLOWED_SECONDS,
      dismissalReason: null,
      gameStartPhase: 'idle' as const,
      // 캔버스 슬라이스 리셋
      activeRoundKey: "face",
      selectedToolKey: "pencil",
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
      // 라운드별 누적 가드 — 빠뜨리면 다음 방의 PART_TIME_UP에서 자동제출이 막힌다.
      submittedUserUuids: [],
      roundSubmitted: { face: false, body: false, legs: false },
      roundDeadlines: { face: null, body: null, legs: null },
      // 결과 슬라이스 리셋
      completedAt: null,
      resultItems: [],
      activeResultIndex: 0,
    });
  },
});
