import type { StateCreator } from 'zustand'

import { PART_TO_ROUND_KEY, RELAY_COLORS, RELAY_ROUND_ORDER } from '../constants'
import type { RelayDrawLine, RelayRoundLines } from '../types'

import type { CanvasSlice, RelayDrawingStore } from './store.types'

const DEFAULT_STROKE_WIDTH = 4
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
  selectedColor: RELAY_COLORS[1],
  strokeWidth: DEFAULT_STROKE_WIDTH,
  roundLines: DEFAULT_ROUND_LINES,

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

  // 라운드 전환 애니메이션
  isTransitioning: false,

  // WS 이벤트 전용 effect 트리거 카운터
  partFetchTrigger: 0,

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
  setStrokeWidth: (strokeWidth) => set({ strokeWidth }),

  commitLine: (line) => {
    const { activeRoundKey, roundLines } = get()
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: [...roundLines[activeRoundKey], line],
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
    const { activeRoundKey, roundLines } = get()
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: roundLines[activeRoundKey].slice(0, -1),
      },
    })
  },

  clearRoundLines: () => {
    const { activeRoundKey, roundLines } = get()
    set({
      roundLines: {
        ...roundLines,
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
      // 제출 상태 리셋
      isSubmitting: false,
      isSubmitted: false,
      submittedCount: 0,
      totalCount: 0,
      isTransitioning: false,
      selectedToolKey: 'pencil',
    })
  },

  setPartDeadlineAt: (deadline) => set({ partDeadlineAt: deadline }),

  incrementPartFetchTrigger: () =>
    set((state) => ({ partFetchTrigger: state.partFetchTrigger + 1 })),

  setIsSubmitting: (isSubmitting) => set({ isSubmitting }),

  markSubmitted: () => set({ isSubmitting: false, isSubmitted: true }),

  updateSubmissionProgress: (submittedCount, totalCount) =>
    set({ submittedCount, totalCount }),

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
      isTransitioning: false,
      partFetchTrigger: 0,
    }),
})
