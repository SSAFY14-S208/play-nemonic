import { useRef } from 'react'
import type { AnimationAction } from 'three'
import {
  HUB_PERFORMANCE_PROFILES,
  HUB_PRINTER_POSITION,
  HUB_PRINTER_ROTATION,
  HUB_PRINTER_SCALE,
  HUB_ROOM_MODEL_INCLUDES_PRINTER,
} from '@/shared/constants'
import type { HubPerformanceMode } from '@/shared/types'
import { NemonicPrinterMesh } from '@/worlds/_shared/mesh'
import { useNemonicPrinterStation } from './hooks'

export default function NemonicPrinterStation({
  performanceMode,
}: {
  performanceMode: HubPerformanceMode
}) {
  const actionsRef = useRef<Record<string, AnimationAction | null>>({})
  const performanceProfile = HUB_PERFORMANCE_PROFILES[performanceMode]

  useNemonicPrinterStation(actionsRef)

  if (HUB_ROOM_MODEL_INCLUDES_PRINTER) return null

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
