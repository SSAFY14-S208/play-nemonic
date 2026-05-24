import { useRef } from "react";
import * as THREE from "three";

const PRINT_BUTTON_ANIMATIONS = ["print_button_click", "label_up"] as const;
const LID_ANIMATIONS = ["print_head_up", "toggle_side_button"] as const;

const HAPTIC = {
  BUTTON_CLICK: 80,
  LID_OPEN: [80, 40, 200],
  LID_CLOSE: [200, 40, 80],
  PRINT_START: [
    80, 30, 80, 30, 80, 30, 80, 30, 80, 30, 80, 30, 80, 30, 80, 30, 80, 30,
    80, 30, 80, 30, 80, 30, 80, 30, 80, 30, 80, 30, 50,
  ],
} as const;

type LidState = "closed" | "opening" | "open" | "closing";
type AnimationDirection = "forward" | "reverse";
type AnimationFinishedEvent = {
  action?: THREE.AnimationAction;
};

function vibrate(pattern: number | readonly number[]) {
  if (typeof navigator !== "undefined" && navigator.vibrate) {
    navigator.vibrate(pattern as number | number[]);
  }
}

function playAudio(soundPath: string) {
  if (typeof Audio === "undefined") return;

  void new Audio(soundPath).play().catch(() => undefined);
}

function isAnimationFinishedEvent(
  event: unknown,
): event is AnimationFinishedEvent {
  return typeof event === "object" && event !== null && "action" in event;
}

function playAnimation(action: THREE.AnimationAction, direction: AnimationDirection) {
  action.setLoop(THREE.LoopOnce, 1);
  action.clampWhenFinished = true;
  action.enabled = true;
  action.paused = false;
  action.timeScale = direction === "forward" ? 1 : -1;

  if (direction === "forward") {
    action.reset().play();
    return;
  }

  action.time = action.getClip().duration;
  action.play();
}

function playAnimations(
  actions: Record<string, THREE.AnimationAction | null>,
  animationNames: readonly string[],
  direction: AnimationDirection,
  onComplete: () => void,
) {
  const playableActions = animationNames
    .map((animationName) => actions[animationName])
    .filter((action): action is THREE.AnimationAction => Boolean(action));

  if (playableActions.length === 0) {
    onComplete();
    return;
  }

  const pendingActions = new Set(playableActions);
  const cleanupCallbacks: Array<() => void> = [];
  let hasCompleted = false;

  const completeAction = (action: THREE.AnimationAction) => {
    pendingActions.delete(action);

    if (pendingActions.size > 0 || hasCompleted) return;

    hasCompleted = true;
    cleanupCallbacks.forEach((cleanup) => cleanup());
    onComplete();
  };

  playableActions.forEach((action) => {
    const mixer = action.getMixer();
    const handleFinished = (event: unknown) => {
      if (!isAnimationFinishedEvent(event) || event.action !== action) return;

      completeAction(action);
    };

    mixer.addEventListener("finished", handleFinished);
    cleanupCallbacks.push(() => {
      mixer.removeEventListener("finished", handleFinished);
    });
  });

  playableActions.forEach((action) => {
    playAnimation(action, direction);
  });
}

export function useNemonicPrinterInteraction() {
  const actionsRef = useRef<Record<string, THREE.AnimationAction | null>>({});
  const lidStateRef = useRef<LidState>("closed");
  const isPrintInProgressRef = useRef(false);
  const pendingPrintAfterLidCloseRef = useRef(false);

  const playPrintSequence = () => {
    if (lidStateRef.current !== "closed" || isPrintInProgressRef.current) {
      return;
    }

    pendingPrintAfterLidCloseRef.current = false;
    isPrintInProgressRef.current = true;
    vibrate([HAPTIC.BUTTON_CLICK, 50, ...HAPTIC.PRINT_START]);
    playAudio("/sounds/print_label.mp3");

    playAnimations(
      actionsRef.current,
      PRINT_BUTTON_ANIMATIONS,
      "forward",
      () => {
        isPrintInProgressRef.current = false;
      },
    );
  };

  const closeLid = () => {
    if (lidStateRef.current === "closed") {
      if (pendingPrintAfterLidCloseRef.current) {
        playPrintSequence();
      }
      return;
    }

    if (lidStateRef.current === "closing" || lidStateRef.current === "opening") {
      return;
    }

    lidStateRef.current = "closing";
    vibrate(HAPTIC.LID_CLOSE);
    playAudio("/sounds/close_printer_lid.mp3");

    playAnimations(actionsRef.current, LID_ANIMATIONS, "reverse", () => {
      lidStateRef.current = "closed";

      if (pendingPrintAfterLidCloseRef.current) {
        playPrintSequence();
      }
    });
  };

  const openLid = () => {
    if (
      lidStateRef.current !== "closed" ||
      isPrintInProgressRef.current ||
      pendingPrintAfterLidCloseRef.current
    ) {
      return;
    }

    lidStateRef.current = "opening";
    vibrate(HAPTIC.LID_OPEN);
    playAudio("/sounds/open_printer_lid.mp3");

    playAnimations(actionsRef.current, LID_ANIMATIONS, "forward", () => {
      lidStateRef.current = "open";

      if (pendingPrintAfterLidCloseRef.current) {
        closeLid();
      }
    });
  };

  const handlePrintButtonClick = () => {
    if (isPrintInProgressRef.current) return;

    if (lidStateRef.current === "closed") {
      playPrintSequence();
      return;
    }

    pendingPrintAfterLidCloseRef.current = true;
    closeLid();
  };

  const handleOpenButtonClick = () => {
    if (lidStateRef.current === "open") {
      closeLid();
      return;
    }

    openLid();
  };

  return { actionsRef, handlePrintButtonClick, handleOpenButtonClick };
}
