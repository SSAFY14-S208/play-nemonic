import { Suspense, useState } from "react";
import Lighting from "../_infra/Lighting";
import LandingCamera from "./LandingCamera";
import { useNemonicPrinterInteraction } from "../_shared/hooks";
import { NemonicPrinterMesh } from "../_shared/mesh";
import { NEMONIC_PRINTER_POSITION } from "./constants";
import LandingInteractionHints from "./LandingInteractionHints";

const NEMONIC_WHITE_PLASTIC_MATERIAL_NAMES = [
  "nemonic_plastic_base_white",
  "nemonic_plastic_base_white.001",
];

export default function LandingScene() {
  const [showInteractionHints, setShowInteractionHints] = useState(true);
  const { actionsRef, handlePrintButtonClick, handleOpenButtonClick } =
    useNemonicPrinterInteraction();

  const handleHintedPrintButtonClick = () => {
    setShowInteractionHints(false);
    handlePrintButtonClick();
  };

  const handleHintedOpenButtonClick = () => {
    setShowInteractionHints(false);
    handleOpenButtonClick();
  };

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
          onPrintButtonClick={handleHintedPrintButtonClick}
          onOpenButtonClick={handleHintedOpenButtonClick}
          withPhysics={false}
        />
        <group position={NEMONIC_PRINTER_POSITION}>
          <LandingInteractionHints
            visible={showInteractionHints}
            onPrintHintClick={handleHintedPrintButtonClick}
            onOpenHintClick={handleHintedOpenButtonClick}
          />
        </group>
      </Suspense>
    </>
  );
}
