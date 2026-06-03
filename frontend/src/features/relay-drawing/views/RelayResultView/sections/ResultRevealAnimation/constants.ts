import {
  RELAY_FINAL_STAGE_SIZE,
  RELAY_ROUND_RULES,
  RELAY_STAGE_SIZE,
  type RelayRoundKey,
} from '@/features/relay-drawing/constants'

export const BOX_ASPECT_RATIO = `${RELAY_STAGE_SIZE.width} / ${RELAY_FINAL_STAGE_SIZE.height}`

export function getSliceTopPct(roundKey: RelayRoundKey): number {
  return (
    (RELAY_ROUND_RULES[roundKey].finalOffsetY / RELAY_FINAL_STAGE_SIZE.height) *
    100
  )
}

export const SLICE_HEIGHT_PCT =
  (RELAY_STAGE_SIZE.height / RELAY_FINAL_STAGE_SIZE.height) * 100

export const TILT_BY_ROUND: Record<RelayRoundKey, number> = {
  face: 10,
  body: -15,
  legs: 5,
}

export const TILT_HOLD_MS = 400
export const STRAIGHTEN_HOLD_MS = 250
