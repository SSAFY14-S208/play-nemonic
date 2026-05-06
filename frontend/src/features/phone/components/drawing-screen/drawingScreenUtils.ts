import {
  PHONE_MAX_BRUSH_SIZE,
  PHONE_MIN_BRUSH_SIZE,
} from '../../constants'

export function getBrushSizePercent(brushSize: number) {
  return (
    ((brushSize - PHONE_MIN_BRUSH_SIZE) /
      (PHONE_MAX_BRUSH_SIZE - PHONE_MIN_BRUSH_SIZE)) *
    100
  )
}
