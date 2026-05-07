import { useEffect, useRef } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import type { AnimationAction, AnimationMixer } from "three";
import {
  DANCE_ANIMATION,
  DANCE_KEY_CODE,
  FADE_DURATION,
  IDLE_ANIMATION,
  WALK_ANIMATION,
} from "../constants";

type ActionMap = Record<string, AnimationAction | null>;

export function useCharacterAnimation(
  actions: ActionMap,
  isMovingRef: React.RefObject<boolean>,
) {
  const isDancingRef = useRef(false);
  const prevIsMovingRef = useRef(false);

  // 마운트 시 idle 재생
  useEffect(() => {
    actions[IDLE_ANIMATION]?.reset().play();
  }, [actions]);

  // 이동 상태 전환 → idle ↔ walk (댄스 중엔 전환 막음)
  useFrame(() => {
    const isNowMoving = isMovingRef.current;
    if (isNowMoving === prevIsMovingRef.current) return;
    prevIsMovingRef.current = isNowMoving;

    if (isNowMoving) {
      isDancingRef.current = false;
      actions[IDLE_ANIMATION]?.fadeOut(FADE_DURATION);
      actions[DANCE_ANIMATION]?.fadeOut(FADE_DURATION);
      actions[WALK_ANIMATION]?.reset().fadeIn(FADE_DURATION).play();
    } else {
      actions[WALK_ANIMATION]?.fadeOut(FADE_DURATION);
      actions[IDLE_ANIMATION]?.reset().fadeIn(FADE_DURATION).play();
    }
  });

  // 댄스 트리거 — D 키
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.code !== DANCE_KEY_CODE) return;
      if (isDancingRef.current) return;
      if (isMovingRef.current) return;

      const danceAction = actions[DANCE_ANIMATION];
      if (!danceAction) return;

      isDancingRef.current = true;

      actions[IDLE_ANIMATION]?.fadeOut(FADE_DURATION);
      actions[WALK_ANIMATION]?.fadeOut(FADE_DURATION);

      danceAction.reset();
      danceAction.setLoop(THREE.LoopOnce, 1);
      danceAction.clampWhenFinished = true;
      danceAction.fadeIn(FADE_DURATION).play();

      const mixer: AnimationMixer = danceAction.getMixer();
      const handleFinished = (event: { action: AnimationAction }) => {
        if (event.action !== danceAction) return;
        mixer.removeEventListener("finished", handleFinished);

        isDancingRef.current = false;
        danceAction.fadeOut(FADE_DURATION);

        const nextAnimation = isMovingRef.current
          ? WALK_ANIMATION
          : IDLE_ANIMATION;
        actions[nextAnimation]?.reset().fadeIn(FADE_DURATION).play();
        prevIsMovingRef.current = isMovingRef.current;
      };

      mixer.addEventListener("finished", handleFinished);
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [actions, isMovingRef]);
}
