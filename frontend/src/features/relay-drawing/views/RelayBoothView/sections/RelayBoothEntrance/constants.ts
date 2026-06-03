import {
  greenNemoConfused,
  greenNemoMoved,
  greenNemoSatisfied,
  redNemoConfused,
  redNemoMoved,
  redNemoSatisfied,
} from '@/features/relay-drawing/assets'

import type { ChoreographyPhase } from './types'

export const PART_WIDTH_SM = 120
export const PART_WIDTH_LG = 180
export const PART_ASPECT_RATIO = 3 / 2
export const PART_GAP = 4
export const VIEWPORT_FILL_RATIO = 0.85
export const PART_HOLD_MS = 150
export const FAN_BREAKPOINT_PX = 1024

export const CAMERA_PAN_TRANSITION = {
  type: 'tween' as const,
  duration: 0.4,
  ease: [0.32, 0.72, 0, 1] as [number, number, number, number],
}

export const SLOT_TRANSITION = {
  type: 'spring' as const,
  stiffness: 200,
  damping: 26,
  mass: 0.7,
}

export const SIDE_ROTATION_TRANSITION = {
  duration: 0.5,
  ease: [0.34, 1.2, 0.5, 1] as [number, number, number, number],
}

export const NEMO_TRANSITION = {
  duration: 0.3,
  ease: 'easeInOut' as const,
}

export const FAN_TARGETS = {
  right: { sm: { rotate: 15, x: 60 }, lg: { rotate: 15, x: 120 } },
  left: { sm: { rotate: 5, x: -100 }, lg: { rotate: 5, x: -200 } },
} as const

export const NEMO_ASSETS_BY_INTRO_PHASE = {
  'intro-1': { red: redNemoMoved, green: greenNemoMoved },
  'intro-2': { red: redNemoConfused, green: greenNemoConfused },
  'intro-3': { red: redNemoSatisfied, green: greenNemoSatisfied },
} as const

export const INTRO_PHASES = ['intro-1', 'intro-2', 'intro-3'] as const

export const REVEAL_COUNT_BY_PHASE: Record<ChoreographyPhase, number> = {
  'intro-1': 1,
  'intro-2': 2,
  'intro-3': 3,
  settling: 3,
  fanning: 3,
}

export const FOCUS_INDEX_BY_PHASE: Record<ChoreographyPhase, number> = {
  'intro-1': 0,
  'intro-2': 1,
  'intro-3': 2,
  settling: 1,
  fanning: 1,
}

export const EXPECTED_REVEAL_INDEX_BY_PHASE: Record<
  ChoreographyPhase,
  number | null
> = {
  'intro-1': 0,
  'intro-2': 1,
  'intro-3': 2,
  settling: null,
  fanning: null,
}
