import { useRef } from "react";
import * as THREE from "three";
import type { AnimationAction } from "three";

// GLB AnimationClip 이름 (스크린샷으로 확인된 실제 이름)
const PRINT_BUTTON_ANIMATIONS = ["print_button_click", "label_up"] as const;
const LID_ANIMATIONS = ["print_head_up", "toggle_side_button"] as const;

export function useNemonicPrinterInteraction() {
  const actionsRef = useRef<Record<string, AnimationAction | null>>({});
  const isLidOpenRef = useRef(false);

  const handlePrintButtonClick = () => {
    void new Audio("/sounds/print_label.mp3").play();

    for (const name of PRINT_BUTTON_ANIMATIONS) {
      const action = actionsRef.current[name];
      if (!action) continue;
      action.setLoop(THREE.LoopOnce, 1);
      action.clampWhenFinished = true;
      action.timeScale = 1;
      action.reset().play();
    }
  };

  const handleOpenButtonClick = () => {
    if (isLidOpenRef.current) {
      // 닫기: 역재생 + close 사운드
      void new Audio("/sounds/close_printer_lid.mp3").play();
      for (const name of LID_ANIMATIONS) {
        const action = actionsRef.current[name];
        if (!action) continue;
        action.timeScale = -1;
        // clampWhenFinished로 duration에 멈춰 있을 때 역재생
        // 한 번도 열지 않았을 경우 time이 0이므로 duration으로 강제 설정
        if (action.time === 0) action.time = action.getClip().duration;
        action.paused = false;
        action.enabled = true;
        action.play();
      }
      isLidOpenRef.current = false;
    } else {
      // 열기: 정재생 + open 사운드
      void new Audio("/sounds/open_printer_lid.mp3").play();
      for (const name of LID_ANIMATIONS) {
        const action = actionsRef.current[name];
        if (!action) continue;
        action.setLoop(THREE.LoopOnce, 1);
        action.clampWhenFinished = true;
        action.timeScale = 1;
        action.reset().play();
      }
      isLidOpenRef.current = true;
    }
  };

  return { actionsRef, handlePrintButtonClick, handleOpenButtonClick };
}
