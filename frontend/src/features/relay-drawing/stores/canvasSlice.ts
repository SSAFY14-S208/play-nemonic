import type { StateCreator } from 'zustand'

import {
  DEFAULT_DRAWING_STROKE_WIDTH,
  DRAWING_COLORS,
  MAX_RECENT_DRAWING_COLOR_COUNT,
} from '@/shared/constants'
import { PART_TO_ROUND_KEY, RELAY_ROUND_ORDER } from '../constants'
import type { RelayDrawLine, RelayRoundLines } from '../types'

import type { CanvasSlice, RelayDrawingStore } from './store.types'

const DEFAULT_ROUND_LINES: RelayRoundLines = {
  face: [],
  body: [],
  legs: [],
}

export const createCanvasSlice: StateCreator<RelayDrawingStore, [], [], CanvasSlice> = (
  set,
  get,
) => ({
  activeRoundKey: 'face',
  selectedToolKey: 'pencil',
  // 검은색(팔레트 첫 색)을 기본으로 — 가장 무난하고 라인이 또렷하게 보인다.
  selectedColor: DRAWING_COLORS[0],
  selectedOpacity: 1,
  strokeWidth: DEFAULT_DRAWING_STROKE_WIDTH,
  recentColors: [],
  roundLines: DEFAULT_ROUND_LINES,
  roundRedoStack: DEFAULT_ROUND_LINES,

  // 서버 배정 필드 — 초기값. 모두 fetch 응답으로 setAssignment가 채운다.
  canvasIndex: null,
  currentPart: null,
  partDeadlineAt: null,
  hintImageUrl: null,

  // 제출 상태
  isSubmitting: false,
  isSubmitted: false,
  submittedCount: 0,
  totalCount: 0,
  submittedUserUuids: [],

  // 라운드별 데드라인/제출 상태
  roundDeadlines: { face: null, body: null, legs: null },
  roundSubmitted: { face: false, body: false, legs: false },

  // 라운드 전환 애니메이션
  isTransitioning: false,

  // PART_TIME_UP 오버레이 플래그
  isPartTimeUp: false,

  // WS 이벤트 전용 effect 트리거 카운터
  partFetchTrigger: 0,

  // PART_TIME_UP → 본인이 미제출자일 때 increment (자동 제출 트리거)
  pendingAutoSubmitTrigger: 0,

  setActiveRoundKey: (roundKey) => {
    set({ activeRoundKey: roundKey })
  },

  completeRound: () => {
    const { activeRoundKey } = get()
    const activeRoundIndex = RELAY_ROUND_ORDER.findIndex(
      (roundKey) => roundKey === activeRoundKey,
    )
    const nextRoundKey = RELAY_ROUND_ORDER[activeRoundIndex + 1]

    if (nextRoundKey) {
      set({ activeRoundKey: nextRoundKey })
      return
    }

    // 마지막 라운드 — roomStatus 전환은 서버 ALL_PARTS_COMPLETED/RESULT_CREATED
    // 이벤트가 결정한다. 여기서는 result 슬라이스의 로컬 마무리 상태만 박는다.
    set({
      completedAt: new Date().toISOString(),
      resultRevealStep: 'final',
    })
  },

  setSelectedToolKey: (toolKey) => set({ selectedToolKey: toolKey }),
  setSelectedColor: (color) => set({ selectedColor: color }),
  setSelectedOpacity: (opacity) => set({ selectedOpacity: opacity }),
  setStrokeWidth: (strokeWidth) => set({ strokeWidth }),
  addRecentColor: (color) => {
    set((state) => {
      const uniqueRecentColors = state.recentColors.filter(
        (recentColor) => recentColor !== color,
      )

      return {
        recentColors: [color, ...uniqueRecentColors].slice(0, MAX_RECENT_DRAWING_COLOR_COUNT),
      }
    })
  },

  commitLine: (line) => {
    const { activeRoundKey, roundLines, roundRedoStack } = get()
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: [...roundLines[activeRoundKey], line],
      },
      // 새 라인이 그려지면 redo 스택은 무의미 — 비워둔다.
      roundRedoStack: {
        ...roundRedoStack,
        [activeRoundKey]: [],
      },
    })
  },

  appendPointToLastLine: (point) => {
    const { activeRoundKey, roundLines } = get()
    const currentLines = roundLines[activeRoundKey]
    const latestLine = currentLines[currentLines.length - 1]
    if (!latestLine) return

    const updatedLine: RelayDrawLine = {
      ...latestLine,
      points: [...latestLine.points, point],
    }

    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: [...currentLines.slice(0, -1), updatedLine],
      },
    })
  },

  undoLine: () => {
    const { activeRoundKey, roundLines, roundRedoStack } = get()
    const currentLines = roundLines[activeRoundKey]
    if (currentLines.length === 0) return
    const poppedLine = currentLines[currentLines.length - 1]
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: currentLines.slice(0, -1),
      },
      roundRedoStack: {
        ...roundRedoStack,
        [activeRoundKey]: [...roundRedoStack[activeRoundKey], poppedLine],
      },
    })
  },

  redoLine: () => {
    const { activeRoundKey, roundLines, roundRedoStack } = get()
    const currentRedoStack = roundRedoStack[activeRoundKey]
    if (currentRedoStack.length === 0) return
    const restoredLine = currentRedoStack[currentRedoStack.length - 1]
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: [...roundLines[activeRoundKey], restoredLine],
      },
      roundRedoStack: {
        ...roundRedoStack,
        [activeRoundKey]: currentRedoStack.slice(0, -1),
      },
    })
  },

  clearRoundLines: () => {
    const { activeRoundKey, roundLines, roundRedoStack } = get()
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: [],
      },
      // 전체 비우기는 되돌릴 수 없는 액션 — redo 스택도 같이 초기화.
      roundRedoStack: {
        ...roundRedoStack,
        [activeRoundKey]: [],
      },
    })
  },

  // getRelayRoomAssignmentMe 응답으로 배정 세팅 + 캔버스 초기화.
  // 새 파트마다 호출되어 이전 라운드 그림을 비우고 힌트를 교체한다.
  setAssignment: (assignment) => {
    const roundKey = PART_TO_ROUND_KEY[assignment.part]
    // FACE 라운드는 이전 파트가 없어서 hint가 null로 내려온다(가이드 §16).
    // BODY/LEGS는 객체이고 empty=true면 빈 힌트.
    const hintImageUrl =
      assignment.hint && !assignment.hint.empty ? assignment.hint.url : null
    set({
      canvasIndex: assignment.canvasIndex,
      currentPart: assignment.part,
      // partDeadlineAt은 WS(GAME_STARTED/PART_STARTED)와 REST hydration에서만 관리.
      // assignment API 응답의 deadline이 stale할 수 있으므로 여기서는 건드리지 않는다.
      hintImageUrl,
      activeRoundKey: roundKey,
      // 새 배정이 들어오면 캔버스를 초기화한다 — 이전 라운드 라인은 다른 캔버스의 것이라 무의미.
      roundLines: { face: [], body: [], legs: [] },
      roundRedoStack: { face: [], body: [], legs: [] },
      // 제출 상태 리셋
      isSubmitting: false,
      isSubmitted: false,
      submittedCount: 0,
      totalCount: 0,
      submittedUserUuids: [],
      isTransitioning: false,
      // 새 배정이 들어왔다는 건 PART_STARTED 흐름이 끝난 시점이라
      // 이전 라운드의 PART_TIME_UP 오버레이는 더 이상 의미 없음.
      isPartTimeUp: false,
      selectedToolKey: 'pencil',
      selectedOpacity: 1,
      strokeWidth: DEFAULT_DRAWING_STROKE_WIDTH,
      // 라운드별 데드라인 — 새로고침 복귀 시 WS 이벤트 없이도 deadline이 복원되도록.
      // roundSubmitted는 리셋하지 않는다 — 라운드 간 누적 이력.
      roundDeadlines: {
        ...get().roundDeadlines,
        [roundKey]: assignment.partDeadlineAt,
      },
    })
  },

  setPartDeadlineAt: (deadline) => set({ partDeadlineAt: deadline }),

  incrementPartFetchTrigger: () =>
    set((state) => ({ partFetchTrigger: state.partFetchTrigger + 1 })),

  setPartTimeUp: (value) => set({ isPartTimeUp: value }),

  triggerPendingAutoSubmit: () =>
    set((state) => ({ pendingAutoSubmitTrigger: state.pendingAutoSubmitTrigger + 1 })),

  setIsSubmitting: (isSubmitting) => set({ isSubmitting }),

  markSubmitted: (roundKey?) => {
    const targetRound = roundKey ?? get().activeRoundKey
    const isSameRound = targetRound === get().activeRoundKey
    set({
      // 현재 라운드 제출 완료일 때만 공유 UI 플래그 갱신
      // (이전 라운드의 in-flight 응답이 현재 라운드 UI를 덮어쓰지 않도록)
      isSubmitting: isSameRound ? false : get().isSubmitting,
      isSubmitted: isSameRound ? true : get().isSubmitted,
      roundSubmitted: { ...get().roundSubmitted, [targetRound]: true },
    })
  },

  setRoundDeadline: (roundKey, deadline) =>
    set((state) => ({
      roundDeadlines: { ...state.roundDeadlines, [roundKey]: deadline },
    })),

  updateSubmissionProgress: (submittedCount, totalCount) =>
    set({ submittedCount, totalCount }),

  addSubmittedUserUuid: (userUuid) => {
    const current = get().submittedUserUuids
    if (current.includes(userUuid)) return
    set({ submittedUserUuids: [...current, userUuid] })
  },

  clearSubmittedUserUuids: () => set({ submittedUserUuids: [] }),

  beginTransition: () => set({ isTransitioning: true }),

  advanceToNextRound: () => {
    // 전환 애니메이션 종료 후 호출 — 플래그만 내린다.
    // 실제 라운드 전환(activeRoundKey + 캔버스 초기화)은 setAssignment에서 처리.
    set({ isTransitioning: false })
  },

  clearAssignment: () =>
    set({
      canvasIndex: null,
      currentPart: null,
      partDeadlineAt: null,
      hintImageUrl: null,
      isSubmitting: false,
      isSubmitted: false,
      submittedCount: 0,
      totalCount: 0,
      submittedUserUuids: [],
      isTransitioning: false,
      isPartTimeUp: false,
      partFetchTrigger: 0,
      pendingAutoSubmitTrigger: 0,
      roundDeadlines: { face: null, body: null, legs: null },
      roundSubmitted: { face: false, body: false, legs: false },
    }),
})
