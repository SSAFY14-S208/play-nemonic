import { Suspense, useEffect, useRef, useState } from "react";
import Lighting from "../_infra/Lighting";
import LandingCamera from "./LandingCamera";
import { useNemonicPrinterInteraction } from "../_shared/hooks";
import { NemonicPrinterMesh } from "../_shared/mesh";
import { NEMONIC_PRINTER_POSITION } from "./constants";
import LandingInteractionHints from "./LandingInteractionHints";
import {
  NEMONIC_ROOM_PRINT_EVENT,
  consumeNemonicRoomPrintDraft,
  type NemonicRoomPrintDraft,
} from "@/shared/utils";

const NEMONIC_WHITE_PLASTIC_MATERIAL_NAMES = [
  "nemonic_plastic_base_white",
  "nemonic_plastic_base_white.001",
];
const ROOM_PRINT_ANIMATION_DELAY_MS = 80;

export default function LandingScene() {
  const [showInteractionHints, setShowInteractionHints] = useState(true);
  const [roomPrintDraft, setRoomPrintDraft] =
    useState<NemonicRoomPrintDraft | null>(null);
  const [isDefaultPrintLabelVisible, setDefaultPrintLabelVisible] =
    useState(false);
  const lastPlayedPrintIdRef = useRef<string | null>(null);
  const { actionsRef, handlePrintButtonClick, handleOpenButtonClick } =
    useNemonicPrinterInteraction();

  useEffect(() => {
    const handleNemonicRoomPrint = () => {
      const printDraft = consumeNemonicRoomPrintDraft();
      if (!printDraft) return;

      setShowInteractionHints(false);
      setDefaultPrintLabelVisible(false);
      setRoomPrintDraft(printDraft);
    };

    handleNemonicRoomPrint();
    window.addEventListener(NEMONIC_ROOM_PRINT_EVENT, handleNemonicRoomPrint);
    return () => {
      window.removeEventListener(
        NEMONIC_ROOM_PRINT_EVENT,
        handleNemonicRoomPrint,
      );
    };
  }, []);

  useEffect(() => {
    if (!roomPrintDraft) return;

    const printId = roomPrintDraft.createdAt ?? roomPrintDraft.imageUrl;
    if (lastPlayedPrintIdRef.current === printId) return;

    lastPlayedPrintIdRef.current = printId;
    const timerId = window.setTimeout(() => {
      handlePrintButtonClick();
    }, ROOM_PRINT_ANIMATION_DELAY_MS);

    return () => window.clearTimeout(timerId);
  }, [handlePrintButtonClick, roomPrintDraft]);

  const handleHintedPrintButtonClick = () => {
    setShowInteractionHints(false);
    if (!roomPrintDraft) {
      setDefaultPrintLabelVisible(true);
    }

    window.setTimeout(handlePrintButtonClick, ROOM_PRINT_ANIMATION_DELAY_MS);
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
          isPrintLabelVisible={
            isDefaultPrintLabelVisible || Boolean(roomPrintDraft?.imageUrl)
          }
          printImageUrl={roomPrintDraft?.imageUrl}
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
