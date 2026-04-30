import { useRef, Suspense } from 'react'
import * as THREE from 'three'
import Lighting from '../_infra/Lighting'
import Character from '../_infra/Character'
import DeskMesh from './objects/DeskMesh'
import LandingCamera from './LandingCamera'
import { useLandingInteraction } from './useLandingInteraction'
import { useNemonicPrinterInteraction } from '../_shared/hooks'
import { NemonicPrinterMesh } from '../_shared/mesh'
import { DESK_SURFACE_Y, NEMONIC_PRINTER_POSITION } from './constants'

export default function LandingScene() {
  const targetPositionRef = useRef<THREE.Vector3>(new THREE.Vector3())
  const characterPositionRef = useRef<THREE.Vector3>(
    new THREE.Vector3(0, DESK_SURFACE_Y + 0.85, 0),
  )
  const isPointerDownRef = useRef<boolean>(false)

  const { actionsRef, handlePrintButtonClick, handleOpenButtonClick } =
    useNemonicPrinterInteraction()

  useLandingInteraction(targetPositionRef, isPointerDownRef)

  return (
    <>
      <LandingCamera characterPositionRef={characterPositionRef} />
      <Lighting />
      <Suspense fallback={null}>
        <DeskMesh />
      </Suspense>
      <Suspense fallback={null}>
        <NemonicPrinterMesh
          position={NEMONIC_PRINTER_POSITION}
          actionsRef={actionsRef}
          onPrintButtonClick={handlePrintButtonClick}
          onOpenButtonClick={handleOpenButtonClick}
        />
      </Suspense>
      <Suspense fallback={null}>
        <Character
          targetPositionRef={targetPositionRef}
          characterPositionRef={characterPositionRef}
          isPointerDownRef={isPointerDownRef}
          surfaceY={DESK_SURFACE_Y}
        />
      </Suspense>
    </>
  )
}
