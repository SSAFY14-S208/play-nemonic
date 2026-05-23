import { RELAY_STAGE_SIZE } from '../../constants'
import type { RelayDrawPoint } from '../..'

// 한 점이 라운드 영역(주로 drawArea / hint 영역) 안에 들어오는지 검사.
export function isPointInsideArea(
  point: RelayDrawPoint,
  area: { y: number; height: number },
) {
  return (
    point.x >= 0 &&
    point.x <= RELAY_STAGE_SIZE.width &&
    point.y >= area.y &&
    point.y <= area.y + area.height
  )
}
