import { Suspense } from 'react'
import { ContactShadows } from '@react-three/drei'
import { useFrame } from '@react-three/fiber'
import CameraRig from './CameraRig'
import LightingSetup from './LightingSetup'
import MonitorGameSelector from './objects/MonitorGameSelector'
import NemonicPrinterStation from './objects/NemonicPrinterStation'
import PegboardAreaMesh from './objects/PegboardAreaMesh'
import PrintedNoteMesh from './objects/PrintedNoteMesh'
import RoomModel from './objects/RoomModel'
import { HUB_PERFORMANCE_PROFILES } from '@/shared/constants'
import { useHubPrintStore } from '@/shared/stores'
import type { HubPerformanceMode } from '@/shared/types'
import {
  isHubPerformanceDiagnosticsEnabled,
  trackHubFrame,
} from '@/shared/utils'

function HubRenderDiagnostics() {
  useFrame(() => {
    trackHubFrame('hubCanvas')
  })

  return null
}

export default function HubScene({
  performanceMode,
}: {
  performanceMode: HubPerformanceMode
}) {
  const notes = useHubPrintStore((state) => state.notes)
  const performanceProfile = HUB_PERFORMANCE_PROFILES[performanceMode]

  return (
    <>
      <color attach="background" args={['#17112c']} />
      <fog attach="fog" args={['#17112c', 17, 36]} />
      <CameraRig performanceMode={performanceMode} />
      {isHubPerformanceDiagnosticsEnabled(performanceMode) && (
        <HubRenderDiagnostics />
      )}
      <LightingSetup performanceMode={performanceMode} />
      <Suspense fallback={null}>
        <RoomModel performanceMode={performanceMode} />
      </Suspense>
      <Suspense fallback={null}>
        <MonitorGameSelector />
      </Suspense>
      <Suspense fallback={null}>
        <NemonicPrinterStation performanceMode={performanceMode} />
      </Suspense>
      <PegboardAreaMesh />
      {notes.map((note) => (
        <PrintedNoteMesh
          key={note.id}
          note={note}
        />
      ))}
      {performanceProfile.contactShadows && (
        <Suspense fallback={null}>
          <ContactShadows
            blur={2.6}
            color="#9e88cc"
            far={6.5}
            frames={1}
            opacity={0.18}
            position={[-1.05, -0.08, -1.95]}
            resolution={768}
            scale={9.5}
          />
        </Suspense>
      )}
    </>
  )
}
