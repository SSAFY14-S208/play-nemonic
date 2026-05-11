import { Suspense } from "react";
import Lighting from "../_infra/Lighting";
import DeskMesh from "./objects/DeskMesh";
import LandingCamera from "./LandingCamera";
import { useNemonicPrinterInteraction } from "../_shared/hooks";
import { NemonicPrinterMesh } from "../_shared/mesh";
import { NEMONIC_PRINTER_POSITION } from "./constants";

export default function LandingScene() {
  const { actionsRef, handlePrintButtonClick, handleOpenButtonClick } =
    useNemonicPrinterInteraction();

  return (
    <>
      <LandingCamera />
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
          withPhysics={false}
        />
      </Suspense>
    </>
  );
}
