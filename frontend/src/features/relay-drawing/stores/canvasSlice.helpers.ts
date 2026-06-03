import {
  DEFAULT_DRAWING_STROKE_WIDTH,
  DRAWING_COLORS,
  MAX_RECENT_DRAWING_COLOR_COUNT,
} from '@/shared/constants'
import type { RelayRoomMyAssignmentResponse } from '@/shared/types'

import { PART_TO_ROUND_KEY } from '../constants'
import type { RelayRoundLines } from '..'

export const createEmptyRoundLines = (): RelayRoundLines => ({
  face: [],
  body: [],
  legs: [],
})

export const createInitialRoundSubmitted = () => ({
  face: false,
  body: false,
  legs: false,
})

export const createInitialRoundDeadlines = () => ({
  face: null,
  body: null,
  legs: null,
})

export const getUpdatedRecentColors = (recentColors: string[], color: string) => {
  const uniqueRecentColors = recentColors.filter(
    (recentColor) => recentColor !== color,
  )

  return [color, ...uniqueRecentColors].slice(0, MAX_RECENT_DRAWING_COLOR_COUNT)
}

export const getAssignmentHintImageUrl = (
  assignment: RelayRoomMyAssignmentResponse,
) => (assignment.hint && !assignment.hint.empty ? assignment.hint.url : null)

export const createAssignmentDrawingState = (
  assignment: RelayRoomMyAssignmentResponse,
) => ({
  canvasIndex: assignment.canvasIndex,
  currentPart: assignment.part,
  hintImageUrl: getAssignmentHintImageUrl(assignment),
  activeRoundKey: PART_TO_ROUND_KEY[assignment.part],
  roundLines: createEmptyRoundLines(),
  roundRedoStack: createEmptyRoundLines(),
  isSubmitting: false,
  isSubmitted: false,
  submittedCount: 0,
  totalCount: 0,
  submittedUserUuids: [],
  isTransitioning: false,
  isPartTimeUp: false,
  selectedToolKey: 'pencil' as const,
  selectedOpacity: 1,
  strokeWidth: DEFAULT_DRAWING_STROKE_WIDTH,
})

export const createClearedAssignmentState = () => ({
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
  roundDeadlines: createInitialRoundDeadlines(),
  roundSubmitted: createInitialRoundSubmitted(),
})

export const createInitialCanvasState = () => ({
  activeRoundKey: 'face' as const,
  selectedToolKey: 'pencil' as const,
  selectedColor: DRAWING_COLORS[0],
  selectedOpacity: 1,
  strokeWidth: DEFAULT_DRAWING_STROKE_WIDTH,
  recentColors: [],
  roundLines: createEmptyRoundLines(),
  roundRedoStack: createEmptyRoundLines(),
  canvasIndex: null,
  currentPart: null,
  partDeadlineAt: null,
  hintImageUrl: null,
  isSubmitting: false,
  isSubmitted: false,
  submittedCount: 0,
  totalCount: 0,
  submittedUserUuids: [],
  roundDeadlines: createInitialRoundDeadlines(),
  roundSubmitted: createInitialRoundSubmitted(),
  isTransitioning: false,
  isPartTimeUp: false,
  partFetchTrigger: 0,
  pendingAutoSubmitTrigger: 0,
})
