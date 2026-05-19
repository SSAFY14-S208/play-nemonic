import { Suspense } from "react";
import Lighting from "../_infra/Lighting";
import LandingCamera from "./LandingCamera";
import { useNemonicPrinterInteraction } from "../_shared/hooks";
import { NemonicPrinterMesh } from "../_shared/mesh";
import { NEMONIC_PRINTER_POSITION } from "./constants";

const NEMONIC_WHITE_PLASTIC_MATERIAL_NAMES = [
  "nemonic_plastic_base_white",
  "nemonic_plastic_base_white.001",
];

export default function LandingScene() {
  const { actionsRef, handlePrintButtonClick, handleOpenButtonClick } =
    useNemonicPrinterInteraction();

  return (
    <>
      <color attach="background" args={["#ffffff"]} />
      <LandingCamera />
      <Lighting />
      <Suspense fallback={null}>
        <NemonicPrinterMesh
          position={NEMONIC_PRINTER_POSITION}
          actionsRef={actionsRef}
          baseColorOverride="#ffffff"
          baseColorOverrideMaterialNames={NEMONIC_WHITE_PLASTIC_MATERIAL_NAMES}
          highlightStrength="strong"
          onPrintButtonClick={handlePrintButtonClick}
          onOpenButtonClick={handleOpenButtonClick}
          withPhysics={false}
        />
      </Suspense>
    </>
  );
}
