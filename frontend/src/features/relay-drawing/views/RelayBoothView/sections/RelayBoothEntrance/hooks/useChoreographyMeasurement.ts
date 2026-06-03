import { useEffect, useState } from 'react'

import {
  FAN_BREAKPOINT_PX,
  FAN_TARGETS,
  PART_ASPECT_RATIO,
  PART_GAP,
  PART_WIDTH_LG,
  PART_WIDTH_SM,
  VIEWPORT_FILL_RATIO,
} from '../constants'
import type { Measurement } from '../types'

export function useChoreographyMeasurement(
  slotRef: React.RefObject<HTMLDivElement | null>,
) {
  const [measurement, setMeasurement] = useState<Measurement | null>(null)

  useEffect(() => {
    const measure = () => {
      if (!slotRef.current) return
      const partWidth =
        window.innerWidth >= FAN_BREAKPOINT_PX ? PART_WIDTH_LG : PART_WIDTH_SM
      const partHeight = partWidth / PART_ASPECT_RATIO
      const artworkHeight = 3 * partHeight + 2 * PART_GAP
      slotRef.current.style.width = `${partWidth}px`
      slotRef.current.style.height = `${artworkHeight}px`
      const rect = slotRef.current.getBoundingClientRect()
      const isDesktop = window.innerWidth >= FAN_BREAKPOINT_PX

      setMeasurement({
        centerOffset: {
          x: window.innerWidth / 2 - (rect.left + rect.width / 2),
          y: window.innerHeight / 2 - (rect.top + rect.height / 2),
        },
        introScale: Math.min(
          (window.innerHeight * VIEWPORT_FILL_RATIO) / partHeight,
          (window.innerWidth * VIEWPORT_FILL_RATIO) / partWidth,
        ),
        fanRight: isDesktop ? FAN_TARGETS.right.lg : FAN_TARGETS.right.sm,
        fanLeft: isDesktop ? FAN_TARGETS.left.lg : FAN_TARGETS.left.sm,
        partWidth,
        partHeight,
      })
    }

    const animationFrameId = requestAnimationFrame(measure)
    return () => {
      cancelAnimationFrame(animationFrameId)
    }
  }, [slotRef])

  return measurement
}
