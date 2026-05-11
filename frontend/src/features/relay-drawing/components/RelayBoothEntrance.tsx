"use client";

import { motion, useReducedMotion } from "motion/react";
import { useEffect, useRef, useState } from "react";

import { cn } from "@/shared/libs";

import RelayArtworkCard from "./RelayArtworkCard";

type ChoreographyPhase = "assembling" | "fanning" | "translating";

// 카드 한 장의 DOM/시각 사이즈. ChoreographyTree와 FinalState가 동일한 DOM을 사용해야
// gap/rounded/shadow 등 inner CSS가 일치해 swap 시 깜빡임/사이즈 점프가 없다.
const CARD_SIZE = 150;

// 데스크탑(lg+) 뷰포트에서 인트로(assembling·fanning) 동안 카드를 1.5배로 키워
// 시각적 임팩트를 강화한다. translating 단계에서 우측 슬롯으로 이동할 때 1.0으로
// 줄어들어 최종 자리에 자연스럽게 안착한다. 모바일/태블릿은 항상 1.0 유지.
const DESKTOP_INTRO_SCALE = 1.5;
const DESKTOP_BREAKPOINT_PX = 1024;

interface RelayBoothEntranceProps {
  onLeftReveal: () => void;
  className?: string;
}

export default function RelayBoothEntrance({
  onLeftReveal,
  className,
}: RelayBoothEntranceProps) {
  const [phase, setPhase] = useState<ChoreographyPhase>("assembling");
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

function ChoreographyTree({
  phase,
  onPhaseChange,
  onComplete,
  className,
}: ChoreographyTreeProps) {
  const slotRef = useRef<HTMLDivElement>(null);
  // viewport 중앙 → 우측 슬롯 중앙까지의 px 오프셋. 측정 전엔 null이라 안쪽 motion 트리는
  // 마운트하지 않는다 → 첫 프레임엔 슬롯만 비어있고, raf 후 한 번 set되면 그 다음 렌더에
  // cards가 viewport 중앙에서 등장하도록 initial이 정확한 값으로 잡힌다.
  const [centerOffset, setCenterOffset] = useState<{
    x: number;
    y: number;
  } | null>(null);
  const [isDesktop, setIsDesktop] = useState(false);

  useEffect(() => {
    const measure = () => {
      if (!slotRef.current) return;
      const rect = slotRef.current.getBoundingClientRect();
      setCenterOffset({
        x: window.innerWidth / 2 - (rect.left + rect.width / 2),
        y: window.innerHeight / 2 - (rect.top + rect.height / 2),
      });
      setIsDesktop(window.innerWidth >= DESKTOP_BREAKPOINT_PX);
    };
    // 첫 측정도 raf로 비동기화 — useEffect 본문에서 직접 setState 호출 시 React Compiler
    // 컴파일 에러가 나기 때문.
    const raf = requestAnimationFrame(measure);
    window.addEventListener("resize", measure);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", measure);
    };
  }, []);

  const isFannedOrLater = phase === "fanning" || phase === "translating";
  const isTranslating = phase === "translating";
  // 인트로 단계에서만 데스크탑 한정으로 카드를 1.5배. translating 단계에선 1.0으로 안착.
  const introScale = isDesktop ? DESKTOP_INTRO_SCALE : 1;

  return (
    <div ref={slotRef} className={cn("relative", className)}>
      {centerOffset !== null && (
        <motion.div
          className="relative"
          initial={{
            x: centerOffset.x,
            y: centerOffset.y,
            scale: introScale,
          }}
          animate={
            isTranslating
              ? { x: 0, y: 0, scale: 1 }
              : {
                  x: centerOffset.x,
                  y: centerOffset.y,
                  scale: introScale,
                }
          }
          transition={{
            type: "spring",
            stiffness: 180,
            damping: 20,
            mass: 0.8,
          }}
          onAnimationComplete={() => {
            if (isTranslating) onComplete();
          }}
        >
          {/* z-10 BACK — 오른쪽으로 펼침 (+45°). 회전 종료 시 translating으로 진입. */}
          <motion.div
            className="absolute inset-0 z-10 origin-bottom"
            initial={{ opacity: 0, rotate: 0 }}
            animate={
              isFannedOrLater
                ? { opacity: 1, rotate: 45 }
                : { opacity: 0, rotate: 0 }
            }
            transition={{ duration: 0.5, ease: [0.34, 1.2, 0.5, 1] }}
            onAnimationComplete={() => {
              if (phase === "fanning") onPhaseChange("translating");
            }}
          >
            <RelayArtworkCard playAssembly={false} size={CARD_SIZE} />
          </motion.div>

          {/* z-20 MID — 왼쪽으로 펼침 (-45°). */}
          <motion.div
            className="absolute inset-0 z-20 origin-bottom"
            initial={{ opacity: 0, rotate: 0 }}
            animate={
              isFannedOrLater
                ? { opacity: 1, rotate: -45 }
                : { opacity: 0, rotate: 0 }
            }
            transition={{ duration: 0.5, ease: [0.34, 1.2, 0.5, 1] }}
          >
            <RelayArtworkCard playAssembly={false} size={CARD_SIZE} />
          </motion.div>

          {/* z-30 FRONT — 항상 0°. 조립 stagger 주체. */}
          <div className="relative z-30">
            <RelayArtworkCard
              playAssembly
              size={CARD_SIZE}
              onAssemblyComplete={() => {
                if (phase === "assembling") onPhaseChange("fanning");
              }}
            />
          </div>
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
        <RelayArtworkCard playAssembly={false} size={CARD_SIZE} />
      </div>
      <div className="absolute inset-0 z-20 origin-bottom -rotate-45">
        <RelayArtworkCard playAssembly={false} size={CARD_SIZE} />
      </div>
      <div className="relative z-30">
        <RelayArtworkCard playAssembly={false} size={CARD_SIZE} />
      </div>
    </div>
  );
}
