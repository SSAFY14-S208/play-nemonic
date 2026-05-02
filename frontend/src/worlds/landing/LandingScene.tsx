import { useRef, Suspense } from "react";
import * as THREE from "three";
import Lighting from "../_infra/Lighting";
import Character from "../_infra/Character";
import DeskMesh from "./objects/DeskMesh";
import DeskBoundsMesh from "./objects/DeskBoundsMesh";
import LandingCamera from "./LandingCamera";
import { useLandingInteraction } from "./useLandingInteraction";
import { useNemonicPrinterInteraction } from "../_shared/hooks";
import { NemonicPrinterMesh } from "../_shared/mesh";
import { CAPSULE_HALF_HEIGHT, CAPSULE_RADIUS } from "../_infra/constants";
import { DESK_SURFACE_Y, NEMONIC_PRINTER_POSITION, CHARACTER_INITIAL_POSITION } from "./constants";

export default function LandingScene() {
  const targetPositionRef = useRef<THREE.Vector3>(new THREE.Vector3());
  // 첫 프레임 카메라 스냅 위치 — 실제 캐릭터 RigidBody Y와 일치시켜야 카메라가 튀지 않음
  const characterPositionRef = useRef<THREE.Vector3>(
    new THREE.Vector3(0, DESK_SURFACE_Y + CAPSULE_HALF_HEIGHT + CAPSULE_RADIUS, 0),
  );
  const isPointerDownRef = useRef<boolean>(false);

  const { actionsRef, handlePrintButtonClick, handleOpenButtonClick } =
    useNemonicPrinterInteraction();

  useLandingInteraction(targetPositionRef, isPointerDownRef);

  return (
    <>
      <LandingCamera characterPositionRef={characterPositionRef} />
      <Lighting />
      <Suspense fallback={null}>
        <DeskMesh />
      </Suspense>
      <DeskBoundsMesh />
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
          initialPosition={CHARACTER_INITIAL_POSITION}
        />
      </Suspense>
    </>
  );
}
