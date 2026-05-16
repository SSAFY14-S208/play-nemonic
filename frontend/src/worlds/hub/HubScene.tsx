import { Suspense } from 'react'
import { ContactShadows } from '@react-three/drei'
import { useFrame } from '@react-three/fiber'
import CameraRig from './CameraRig'
import HubPostProcessing from './HubPostProcessing'
import LightingSetup from './LightingSetup'
import HubSkyDome from './objects/HubSkyDome'
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
  const sceneBackgroundColor = performanceProfile.environment ? '#f2edf7' : '#17112c'
  const sceneFogColor = performanceProfile.environment ? '#f2edf7' : '#17112c'

  return (
    <>
      <color attach="background" args={[sceneBackgroundColor]} />
      <fog attach="fog" args={[sceneFogColor, 17, 36]} />
      {performanceProfile.environment && !performanceProfile.environmentBackground && (
        <Suspense fallback={null}>
          <HubSkyDome />
        </Suspense>
      )}
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
      {performanceProfile.contactShadow && (
        <Suspense fallback={null}>
          <ContactShadows
            blur={performanceProfile.contactShadow.blur}
            color={performanceProfile.contactShadow.color}
            far={performanceProfile.contactShadow.far}
            frames={performanceProfile.contactShadow.frames}
            opacity={performanceProfile.contactShadow.opacity}
            position={[-1.05, -0.08, -1.95]}
            resolution={performanceProfile.contactShadow.resolution}
            scale={performanceProfile.contactShadow.scale}
          />
        </Suspense>
      )}
      <HubPostProcessing performanceMode={performanceMode} />
    </>
  )
}
