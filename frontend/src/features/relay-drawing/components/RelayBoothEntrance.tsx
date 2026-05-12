"use client";

import { motion, useReducedMotion } from "motion/react";
import { useCallback, useEffect, useRef, useState } from "react";

import { cn } from "@/shared/libs";

import RelayArtworkCard from "./RelayArtworkCard";

// 한 part(label card)의 px 크기. RelayLabelCard와 동일 단위. 슬롯의 폭/높이는 3*PART_SIZE + 2*PART_GAP.
const PART_SIZE = 150;
// part 간 vertical gap (RelayArtworkCard가 gap-1 = 4px로 쌓는 값과 일치).
const PART_GAP = 4;
// part 1칸 step. 카메라가 한 part씩 panning할 때 이동량의 base.
const PART_STEP = PART_SIZE + PART_GAP;

// 인트로 단계에서 한 part가 viewport 높이의 몇 %를 차지하도록 카메라를 줌인할지.
// 0.85 → 한 part가 viewport 세로 ~85% 점유 (reference 이미지의 시점과 일치).
const VIEWPORT_FILL_RATIO = 0.85;

// 각 part가 spring으로 안착한 후 다음 카메라 pan을 시작하기 전 잠깐 머무는 시간(ms).
// 0이면 ease 없이 연속 pan, 100ms 정도면 deliberate camera feel.
const PART_HOLD_MS = 100;

// 카메라 pan 트윈 transition. critically-damped cinematic 이동. spring을 쓰면
// mid-flight 속도 carry-over로 두 번째·세 번째 pan이 점점 빨라지고 overshoot이 발생.
const CAMERA_PAN_TRANSITION = {
  type: "tween" as const,
  duration: 0.35,
  ease: [0.32, 0.72, 0, 1] as [number, number, number, number],
};

// 외부 slot-positioner spring. settling 단계에서 viewport center → slot center로 이동.
// damping을 26으로 올려 land 시 overshoot 최소화 (이전 20보다 단단).
const SLOT_TRANSITION = {
  type: "spring" as const,
  stiffness: 200,
  damping: 26,
  mass: 0.7,
};

// 사이드 카드 opacity fade-in. 카메라 줌아웃이 먼저 시작되도록 0.15s delay → 0.5s에 걸쳐 페이드인.
// rotate는 한 번도 애니메이트되지 않고 ±45°로 고정.
const SIDE_OPACITY_TRANSITION = {
  opacity: {
    duration: 0.5,
    delay: 0.15,
    ease: "easeOut" as const,
  },
};

type ChoreographyPhase = "intro-1" | "intro-2" | "intro-3" | "settling";

const REVEAL_COUNT_BY_PHASE: Record<ChoreographyPhase, number> = {
  "intro-1": 1,
  "intro-2": 2,
  "intro-3": 3,
  settling: 3,
};

// 각 phase에서 카메라가 집중할 part index. (0=face, 1=body, 2=leg, settling은 정중앙)
const FOCUS_INDEX_BY_PHASE: Record<ChoreographyPhase, number> = {
  "intro-1": 0,
  "intro-2": 1,
  "intro-3": 2,
  settling: 1,
};

// 각 phase에서 다음 phase 전환을 일으킬 reveal index. null이면 더 이상 trigger 없음.
// motion이 phase 변경 후 이미 안착한 part의 onAnimationComplete를 spurious하게 재발화해도,
// 기대하지 않는 index는 무시되어 phase가 역행하지 않는다.
const EXPECTED_REVEAL_INDEX_BY_PHASE: Record<
  ChoreographyPhase,
  number | null
> = {
  "intro-1": 0,
  "intro-2": 1,
  "intro-3": 2,
  settling: null,
};

interface RelayBoothEntranceProps {
  onLeftReveal: () => void;
  className?: string;
}

export default function RelayBoothEntrance({
  onLeftReveal,
  className,
}: RelayBoothEntranceProps) {
  const [phase, setPhase] = useState<ChoreographyPhase>("intro-1");
  const [skipped, setSkipped] = useState(false);
  const [completed, setCompleted] = useState(false);
  const prefersReducedMotion = useReducedMotion();

  const isFinalState = skipped || prefersReducedMotion === true || completed;

  // 진행 중에 화면 어디든 클릭하면 모든 애니메이션을 스킵하고 최종 상태로 즉시 전환.
  useEffect(() => {
    if (isFinalState) return;
    const handleSkip = () => setSkipped(true);
    window.addEventListener("pointerdown", handleSkip, { once: true });
    return () => window.removeEventListener("pointerdown", handleSkip);
  }, [isFinalState]);

  if (isFinalState) {
    return (
      <RelayBoothEntranceFinalState
        onReveal={onLeftReveal}
        className={className}
      />
    );
  }

  return (
    <ChoreographyTree
      phase={phase}
      onPhaseChange={setPhase}
      onComplete={() => setCompleted(true)}
      className={className}
    />
  );
}

interface ChoreographyTreeProps {
  phase: ChoreographyPhase;
  onPhaseChange: (next: ChoreographyPhase) => void;
  onComplete: () => void;
  className?: string;
}

interface Measurement {
  centerOffset: { x: number; y: number };
  introScale: number;
}

function ChoreographyTree({
  phase,
  onPhaseChange,
  onComplete,
  className,
}: ChoreographyTreeProps) {
  const slotRef = useRef<HTMLDivElement>(null);
  // viewport 중앙 → 슬롯 중앙 오프셋과 인트로 카메라 scale을 한 객체로 묶어 단일 nullable state로
  // 관리. null인 동안엔 안쪽 motion 트리를 마운트하지 않아 첫 프레임 점프 방지.
  // resize는 처리하지 않음: 인트로 시퀀스가 2초 이내로 짧고, 중간 리사이즈는 카메라
  // mid-flight retarget으로 시각적 점프를 유발한다. 마운트 1회 측정만 사용.
  const [measurement, setMeasurement] = useState<Measurement | null>(null);
  // hold(다음 phase 전 잠깐 머무는) setTimeout id. unmount 시 cleanup.
  const holdTimerRef = useRef<number | null>(null);

  useEffect(() => {
    const measure = () => {
      if (!slotRef.current) return;
      const rect = slotRef.current.getBoundingClientRect();
      setMeasurement({
        centerOffset: {
          x: window.innerWidth / 2 - (rect.left + rect.width / 2),
          y: window.innerHeight / 2 - (rect.top + rect.height / 2),
        },
        introScale: (window.innerHeight * VIEWPORT_FILL_RATIO) / PART_SIZE,
      });
    };
    // 첫 측정도 raf로 비동기화 — React Compiler가 useEffect 본문 동기 setState를 금지.
    const raf = requestAnimationFrame(measure);
    return () => {
      cancelAnimationFrame(raf);
    };
  }, []);

  useEffect(() => {
    return () => {
      if (holdTimerRef.current !== null) {
        window.clearTimeout(holdTimerRef.current);
        holdTimerRef.current = null;
      }
    };
  }, []);

  const handlePartReveal = useCallback(
    (revealedIndex: number) => {
      // 현재 phase가 기대하는 reveal index가 아니면 무시. motion이 phase 변경 후
      // 이미 안착한 part의 onAnimationComplete를 spurious하게 재발화해도 phase 역행 방지.
      const expected = EXPECTED_REVEAL_INDEX_BY_PHASE[phase];
      if (expected === null || revealedIndex !== expected) return;
      const nextPhase: ChoreographyPhase | null =
        revealedIndex === 0
          ? "intro-2"
          : revealedIndex === 1
            ? "intro-3"
            : revealedIndex === 2
              ? "settling"
              : null;
      if (!nextPhase) return;
      if (holdTimerRef.current !== null) {
        window.clearTimeout(holdTimerRef.current);
      }
      holdTimerRef.current = window.setTimeout(() => {
        holdTimerRef.current = null;
        onPhaseChange(nextPhase);
      }, PART_HOLD_MS);
    },
    [phase, onPhaseChange],
  );

  const isSettling = phase === "settling";
  const focusIndex = FOCUS_INDEX_BY_PHASE[phase];
  const revealCount = REVEAL_COUNT_BY_PHASE[phase];

  return (
    <div ref={slotRef} className={cn("relative", className)}>
      {measurement !== null && (
        <motion.div
          // Layer 1 — slot-positioner: viewport center ↔ slot center translate.
          // 인트로 동안엔 viewport center에 정지, settling에서 slot center로 spring.
          className="relative"
          initial={{
            x: measurement.centerOffset.x,
            y: measurement.centerOffset.y,
          }}
          animate={
            isSettling
              ? { x: 0, y: 0 }
              : {
                  x: measurement.centerOffset.x,
                  y: measurement.centerOffset.y,
                }
          }
          transition={SLOT_TRANSITION}
          onAnimationComplete={() => {
            // settling이 안착하는 시점에만 onComplete 호출. 인트로 동안엔 target이
            // 변하지 않으므로 애니메이션도 일어나지 않아 콜백이 호출되지 않는다(방어용 가드).
            if (isSettling) onComplete();
          }}
        >
          {/* z-10 BACK — +45° 회전한 채 슬롯에 미리 마운트. 카메라 layer 밖에 있어
              인트로 동안 1.0 scale로 존재하지만 opacity 0으로 숨음. settling에서만 fade-in. */}
          <motion.div
            className="absolute inset-0 z-10 origin-bottom"
            initial={{ rotate: 45, opacity: 0 }}
            animate={{ rotate: 45, opacity: isSettling ? 1 : 0 }}
            transition={SIDE_OPACITY_TRANSITION}
          >
            <RelayArtworkCard size={PART_SIZE} />
          </motion.div>

          {/* z-20 MID — -45° 회전 사본. 동일 패턴. */}
          <motion.div
            className="absolute inset-0 z-20 origin-bottom"
            initial={{ rotate: -45, opacity: 0 }}
            animate={{ rotate: -45, opacity: isSettling ? 1 : 0 }}
            transition={SIDE_OPACITY_TRANSITION}
          >
            <RelayArtworkCard size={PART_SIZE} />
          </motion.div>

          {/* Layer 2 — 카메라: 인트로 동안 scale=introScale, translateY=focus offset.
              settling 시 scale=1, translateY=0으로 줌아웃. transform-origin은 default 50% 50%이라
              스케일이 슬롯 중앙을 기준으로 적용되고, translateY로 focus part가 viewport 중앙에 온다. */}
          <motion.div
            className="relative z-30"
            initial={{
              scale: measurement.introScale,
              y: PART_STEP * 1 * measurement.introScale,
            }}
            animate={
              isSettling
                ? { scale: 1, y: 0 }
                : {
                    scale: measurement.introScale,
                    y: PART_STEP * (1 - focusIndex) * measurement.introScale,
                  }
            }
            transition={CAMERA_PAN_TRANSITION}
          >
            <RelayArtworkCard
              revealCount={revealCount}
              onPartReveal={handlePartReveal}
              size={PART_SIZE}
            />
          </motion.div>
        </motion.div>
      )}
    </div>
  );
}

interface RelayBoothEntranceFinalStateProps {
  onReveal: () => void;
  className?: string;
}

function RelayBoothEntranceFinalState({
  onReveal,
  className,
}: RelayBoothEntranceFinalStateProps) {
  // 자연 완료 / 스킵 / reduced-motion 어느 경로든 결국 이 컴포넌트가 마운트되며,
  // 부모의 좌측 페이드 인 트리거를 1회만 발화하도록 ref 플래그로 가드.
  const hasRevealedRef = useRef(false);
  useEffect(() => {
    if (hasRevealedRef.current) return;
    hasRevealedRef.current = true;
    onReveal();
  }, [onReveal]);

  return (
    <div className={cn("relative", className)}>
      <div className="absolute inset-0 z-10 origin-bottom rotate-45">
        <RelayArtworkCard size={PART_SIZE} />
      </div>
      <div className="absolute inset-0 z-20 origin-bottom -rotate-45">
        <RelayArtworkCard size={PART_SIZE} />
      </div>
      <div className="relative z-30">
        <RelayArtworkCard size={PART_SIZE} />
      </div>
    </div>
  );
}
