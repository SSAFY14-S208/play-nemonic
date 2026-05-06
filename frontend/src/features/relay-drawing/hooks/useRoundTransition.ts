"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  RELAY_ROUND_ORDER,
  RELAY_ROUND_RULES,
  type RelayRoundKey,
} from "../constants";
import { useRelayDrawingStore } from "../relayDrawingStore";
import { renderLinesToRasterCanvas } from "../utils";

const TOTAL_DURATION_MS = 3000;

const PHASE_BREAKPOINTS = {
  fadeOut: 0.0,
  frameReveal: 0.12,
  stickerLand: 0.2,
  zoomIn: 0.62,
  fadeIn: 0.82,
  complete: 1.0,
} as const;

export type TransitionPhase =
  | "idle"
  | "fadeOut"
  | "frameReveal"
  | "stickerLand"
  | "zoomIn"
  | "fadeIn";

interface StickerEntry {
  roundKey: RelayRoundKey;
  imageUrl: string;
}

export interface UseRoundTransitionReturn {
  isActive: boolean;
  phase: TransitionPhase;
  phaseProgress: number;
  completedRoundKey: RelayRoundKey | null;
  nextRoundKey: RelayRoundKey | null;
  stickerImageUrl: string | null;
  previousStickers: StickerEntry[];
}

function getPhaseFromProgress(progress: number): TransitionPhase {
  if (progress >= PHASE_BREAKPOINTS.complete) return "idle";
  if (progress >= PHASE_BREAKPOINTS.fadeIn) return "fadeIn";
  if (progress >= PHASE_BREAKPOINTS.zoomIn) return "zoomIn";
  if (progress >= PHASE_BREAKPOINTS.stickerLand) return "stickerLand";
  if (progress >= PHASE_BREAKPOINTS.frameReveal) return "frameReveal";
  return "fadeOut";
}

function getPhaseProgress(progress: number, phase: TransitionPhase): number {
  const phaseStart = PHASE_BREAKPOINTS[phase === "idle" ? "complete" : phase];
  const phaseKeys = Object.keys(PHASE_BREAKPOINTS) as Array<
    keyof typeof PHASE_BREAKPOINTS
  >;
  const phaseIndex = phaseKeys.indexOf(phase === "idle" ? "complete" : phase);
  const phaseEnd =
    phaseIndex < phaseKeys.length - 1
      ? PHASE_BREAKPOINTS[phaseKeys[phaseIndex + 1]]
      : 1.0;

  const phaseDuration = phaseEnd - phaseStart;
  if (phaseDuration <= 0) return 1;

  return Math.min(Math.max((progress - phaseStart) / phaseDuration, 0), 1);
}

export function useRoundTransition(): UseRoundTransitionReturn {
  const isTransitioning = useRelayDrawingStore(
    (state) => state.isTransitioning,
  );
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey);
  const roundLines = useRelayDrawingStore((state) => state.roundLines);
  const advanceToNextRound = useRelayDrawingStore(
    (state) => state.advanceToNextRound,
  );

  const [phase, setPhase] = useState<TransitionPhase>("idle");
  const [phaseProgress, setPhaseProgress] = useState(0);
  const [completedRoundKey, setCompletedRoundKey] =
    useState<RelayRoundKey | null>(null);
  const [nextRoundKey, setNextRoundKey] = useState<RelayRoundKey | null>(null);
  const [stickerImageUrl, setStickerImageUrl] = useState<string | null>(null);
  const [previousStickers, setPreviousStickers] = useState<StickerEntry[]>([]);

  const animationFrameRef = useRef<number | null>(null);
  const startTimeRef = useRef<number>(0);

  const captureRoundImage = useCallback(
    async (roundKey: RelayRoundKey) => {
      const lines = roundLines[roundKey];
      const canvas = await renderLinesToRasterCanvas(lines);
      return canvas?.toDataURL("image/png") ?? null;
    },
    [roundLines],
  );

  useEffect(() => {
    if (!isTransitioning) {
      setPhase("idle");
      setPhaseProgress(0);
      return;
    }

    const currentRoundKey = activeRoundKey;
    const currentRoundIndex = RELAY_ROUND_ORDER.findIndex(
      (roundKey) => roundKey === currentRoundKey,
    );
    const nextKey = RELAY_ROUND_ORDER[currentRoundIndex + 1] ?? null;

    setCompletedRoundKey(currentRoundKey);
    setNextRoundKey(nextKey);

    let cancelled = false;
    (async () => {
      const currentImage = await captureRoundImage(currentRoundKey);
      if (cancelled) return;
      setStickerImageUrl(currentImage);

      const previousEntries: StickerEntry[] = [];
      for (let index = 0; index < currentRoundIndex; index++) {
        const prevRoundKey = RELAY_ROUND_ORDER[index];
        const prevImage = await captureRoundImage(prevRoundKey);
        if (cancelled) return;
        if (prevImage) {
          previousEntries.push({ roundKey: prevRoundKey, imageUrl: prevImage });
        }
      }
      setPreviousStickers(previousEntries);

      if (cancelled) return;

      startTimeRef.current = performance.now();

      const animate = (now: number) => {
        if (cancelled) return;

        const elapsed = now - startTimeRef.current;
        const progress = Math.min(elapsed / TOTAL_DURATION_MS, 1);
        const currentPhase = getPhaseFromProgress(progress);
        const currentPhaseProgress = getPhaseProgress(progress, currentPhase);

        setPhase(currentPhase);
        setPhaseProgress(currentPhaseProgress);

        if (progress >= 1) {
          advanceToNextRound();
          return;
        }

        animationFrameRef.current = requestAnimationFrame(animate);
      };

      animationFrameRef.current = requestAnimationFrame(animate);
    })();

    return () => {
      cancelled = true;
      if (animationFrameRef.current !== null) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }
    };
  }, [isTransitioning, activeRoundKey, advanceToNextRound, captureRoundImage]);

  return {
    isActive: isTransitioning,
    phase,
    phaseProgress,
    completedRoundKey,
    nextRoundKey,
    stickerImageUrl,
    previousStickers,
  };
}
