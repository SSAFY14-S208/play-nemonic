import { useState, useEffect, useRef, useCallback } from "react";
import { useProgress } from "@react-three/drei";

export type LandingPhase = "loading" | "ready" | "welcome" | "done";

const READY_DISPLAY_DURATION = 1500;
const CACHE_FALLBACK_TIMEOUT = 1500;

export default function useLandingProgress() {
  const { progress: loadProgress, active } = useProgress();
  const [phase, setPhase] = useState<LandingPhase>("loading");
  const [displayProgress, setDisplayProgress] = useState(0);
  const hasCompleted = useRef(false);
  const hasStartedLoading = useRef(false);

  // Track whether loading has ever started
  useEffect(() => {
    if (active) {
      hasStartedLoading.current = true;
    }
  }, [active]);

  // Sync display progress from real progress
  useEffect(() => {
    if (hasCompleted.current) return;
    let cancelled = false;
    (async () => {
      if (!cancelled) setDisplayProgress(loadProgress);
    })();
    return () => {
      cancelled = true;
    };
  }, [loadProgress]);

  // Normal completion: real progress reaches 100
  useEffect(() => {
    if (hasCompleted.current || loadProgress < 100) return;
    hasCompleted.current = true;

    let cancelled = false;
    (async () => {
      if (!cancelled) setDisplayProgress(100);

      const audio = new Audio("/sounds/nemonic_on.mp3");
      audio.play().catch(() => {});

      if (!cancelled) setPhase("ready");
    })();
    return () => {
      cancelled = true;
    };
  }, [loadProgress]);

  // Fallback: assets cached in memory, loading never started
  useEffect(() => {
    let cancelled = false;
    (async () => {
      await new Promise((resolve) => setTimeout(resolve, CACHE_FALLBACK_TIMEOUT));
      if (cancelled || hasCompleted.current) return;

      hasCompleted.current = true;
      if (!cancelled) setDisplayProgress(100);

      const audio = new Audio("/sounds/nemonic_on.mp3");
      audio.play().catch(() => {});

      if (!cancelled) setPhase("ready");
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Transition from ready to welcome after delay
  useEffect(() => {
    if (phase !== "ready") return;
    let cancelled = false;
    (async () => {
      await new Promise((resolve) =>
        setTimeout(resolve, READY_DISPLAY_DURATION),
      );
      if (!cancelled) setPhase("welcome");
    })();
    return () => {
      cancelled = true;
    };
  }, [phase]);

  const dismissWelcome = useCallback(() => {
    setPhase("done");
  }, []);

  return { phase, progress: Math.round(displayProgress), dismissWelcome };
}
