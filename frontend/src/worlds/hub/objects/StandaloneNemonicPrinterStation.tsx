import type { MutableRefObject } from 'react'
import type { AnimationAction } from 'three'
import {
  HUB_PERFORMANCE_PROFILES,
  HUB_PRINTER_POSITION,
  HUB_PRINTER_ROTATION,
  HUB_PRINTER_SCALE,
} from '@/shared/constants'
import type { HubPerformanceMode } from '@/shared/types'
import { NemonicPrinterMesh } from '@/worlds/_shared/mesh'

interface StandaloneNemonicPrinterStationProps {
  actionsRef: MutableRefObject<Record<string, AnimationAction | null>>
  performanceMode: HubPerformanceMode
}

export default function StandaloneNemonicPrinterStation({
  actionsRef,
  performanceMode,
}: StandaloneNemonicPrinterStationProps) {
  const performanceProfile = HUB_PERFORMANCE_PROFILES[performanceMode]

  return (
    <NemonicPrinterMesh
      actionsRef={actionsRef}
      modelScale={HUB_PRINTER_SCALE}
      position={HUB_PRINTER_POSITION}
      rotation={HUB_PRINTER_ROTATION}
      withPhysics={false}
      castShadow={performanceProfile.shadows}
      receiveShadow={performanceProfile.shadows}
    />
  )
}
