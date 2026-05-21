import { lazy, useRef } from 'react'
import type { AnimationAction } from 'three'
import { HUB_ROOM_MODEL_INCLUDES_PRINTER } from '@/shared/constants'
import type { HubPerformanceMode } from '@/shared/types'
import { useNemonicPrinterStation } from './hooks'

const StandaloneNemonicPrinterStation = lazy(
  () => import('./StandaloneNemonicPrinterStation'),
)

export default function NemonicPrinterStation({
  performanceMode,
}: {
  performanceMode: HubPerformanceMode
}) {
  const actionsRef = useRef<Record<string, AnimationAction | null>>({})

  useNemonicPrinterStation(actionsRef)

  if (HUB_ROOM_MODEL_INCLUDES_PRINTER) return null

  return (
    <StandaloneNemonicPrinterStation
      actionsRef={actionsRef}
      performanceMode={performanceMode}
    />
  )
}
