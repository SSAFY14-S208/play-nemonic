import { useFrame } from '@react-three/fiber'
import { useEffect, useRef } from 'react'
import type { Group } from 'three'

import {
  FORTUNE_PRINT_DURATION_SECONDS,
  FORTUNE_REDUCED_MOTION_DURATION_SECONDS,
} from '../constants'

interface UseFortunePrinterMotionOptions {
  isPrinting: boolean
  prefersReducedMotion: boolean
  onPrintComplete: () => void
}

export function useFortunePrinterMotion({
  isPrinting,
  prefersReducedMotion,
  onPrintComplete,
}: UseFortunePrinterMotionOptions) {
  const printerGroupRef = useRef<Group>(null)
  const paperGroupRef = useRef<Group>(null)
  const progressRef = useRef(0)
  const completedRef = useRef(false)
  const onPrintCompleteRef = useRef(onPrintComplete)

  useEffect(() => {
    onPrintCompleteRef.current = onPrintComplete
  }, [onPrintComplete])

  useEffect(() => {
    progressRef.current = 0
    completedRef.current = false

    if (paperGroupRef.current) {
      paperGroupRef.current.position.set(0, 0.72, -0.18)
      paperGroupRef.current.rotation.set(-0.18, 0, 0)
      paperGroupRef.current.visible = isPrinting
    }

    if (printerGroupRef.current) {
      printerGroupRef.current.rotation.set(0, Math.PI, 0)
    }
  }, [isPrinting])

  useFrame((_, delta) => {
    const paperGroup = paperGroupRef.current
    const printerGroup = printerGroupRef.current

    if (!paperGroup || !printerGroup || !isPrinting) {
      return
    }

    const duration = prefersReducedMotion
      ? FORTUNE_REDUCED_MOTION_DURATION_SECONDS
      : FORTUNE_PRINT_DURATION_SECONDS

    progressRef.current = Math.min(1, progressRef.current + delta / duration)
    const progress = easeOutCubic(progressRef.current)

    paperGroup.visible = true
    paperGroup.position.z = -0.18 + progress * 1.62
    paperGroup.position.y = 0.72 - Math.sin(progress * Math.PI) * 0.08
    paperGroup.rotation.x = -0.18 + progress * 0.18
    printerGroup.rotation.z = Math.sin(progressRef.current * Math.PI * 18) * 0.012 * (1 - progress)

    if (progressRef.current >= 1 && !completedRef.current) {
      completedRef.current = true
      onPrintCompleteRef.current()
    }
  })

  return {
    paperGroupRef,
    printerGroupRef,
  }
}

function easeOutCubic(progress: number) {
  return 1 - Math.pow(1 - progress, 3)
}
