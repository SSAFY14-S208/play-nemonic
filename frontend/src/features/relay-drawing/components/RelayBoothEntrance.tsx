"use client";

import { motion, useReducedMotion } from "motion/react";
import Image from "next/image";
import { useCallback, useEffect, useRef, useState } from "react";

import { cn } from "@/shared/libs";

import greenNemoConfused from "../assets/green-nemo-confused.png";
import greenNemoMoved from "../assets/green-nemo-moved.png";
import greenNemoSatisfied from "../assets/green-nemo-satisfied.png";
import redNemoConfused from "../assets/red-nemo-confused.png";
import redNemoMoved from "../assets/red-nemo-moved.png";
import redNemoSatisfied from "../assets/red-nemo-satisfied.png";
import RelayArtworkCard from "./RelayArtworkCard";

// 한 part(label card)의 px 크기. RelayLabelCard와 동일 단위. 슬롯의 폭/높이는 3*PART_SIZE + 2*PART_GAP.
const PART_SIZE = 150;
// part 간 vertical gap (RelayArtworkCard가 gap-1 = 4px로 쌓는 값과 일치).
const PART_GAP = 4;
// part 1칸 step. 카메라가 한 part씩 panning할 때 이동량의 base.
const PART_STEP = PART_SIZE + PART_GAP;
// 아트워크 카드 1장의 총 높이 (3 parts + 2 gaps). 슬롯 ref의 명시 height에 사용해,
// 첫 렌더에서 motion 트리가 마운트되지 않아도 slot이 0×0이 아닌 정확한 사이즈를 갖게 한다.
// — measurement 가드로 인해 slot ref가 빈 div가 되면 getBoundingClientRect의 height=0이
// 되고, centerOffset이 slot 중심이 아닌 slot 상단을 viewport 중심으로 끌어와 ARTWORK_HEIGHT/2
// 만큼 아래로 어긋난다 (col-reverse 레이아웃에서만 증상이 보임).
const ARTWORK_HEIGHT = 3 * PART_SIZE + 2 * PART_GAP;

// 인트로 단계에서 한 part가 viewport의 min(width, height) 기준 몇 %를 차지하도록 카메라를 줌인할지.
// 0.85 → 한 part가 viewport 짧은 변 기준 ~85% 점유.
// width와 height 둘 다 고려해 min을 잡지 않으면, 모바일 portrait에서 height 기준 scale이
// width를 초과해 카드가 화면 좌우로 오버플로된다 (예: 375×800에서 scale 4.5 → part 폭 675px).
const VIEWPORT_FILL_RATIO = 0.85;

// 각 part가 spring으로 안착한 후 다음 카메라 pan을 시작하기 전 잠깐 머무는 시간(ms).
// 150ms로 두어 각 part가 화면에 안착한 후 잠깐의 여운을 주고, 하단 nemo 캐릭터가
// 화면 안에 머무는 시간도 충분히 확보. 전체 시퀀스는 ~2.4초.
const PART_HOLD_MS = 150;

// 카메라 pan 트윈 transition. critically-damped cinematic 이동. spring을 쓰면
// mid-flight 속도 carry-over로 두 번째·세 번째 pan이 점점 빨라지고 overshoot이 발생.
const CAMERA_PAN_TRANSITION = {
  type: "tween" as const,
  duration: 0.4,
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

// 사이드 카드 fan-out transition. settling 완료 후 fanning phase에서 rotate(0°→±fanAngle)와
// opacity(0→1)를 동시에 애니메이트. 0.34, 1.2 overshoot easing으로 살짝 튕기듯 펼쳐진다.
const SIDE_ROTATION_TRANSITION = {
  duration: 0.5,
  ease: [0.34, 1.2, 0.5, 1] as [number, number, number, number],
};

// 사이드 카드 fan 각도. 좁은 viewport에선 ±45°일 때 회전된 카드(458px 세로)가 좌우로
// ~302px씩 뻗어 화면 밖으로 크게 잘리므로 모바일/태블릿(< lg = 1024px)에선 30°로 좁힌다.
// FinalState도 동일 breakpoint(Tailwind lg: 기본 1024px)로 분기되어 ChoreographyTree → FinalState
// 전환 시 시각 점프 없음.
const FAN_ANGLE_LG = 45;
const FAN_ANGLE_SM = 30;
const FAN_BREAKPOINT_PX = 1024;

// 좌하단(빨강)/우하단(초록) 네모 캐릭터 fixed pop-up transition. AnimatePresence + key={phase}로
// 각 intro phase마다 enter(y:100%→0%)/exit(y:0%→100%)가 트리거되어 메타포 시퀀스마다
// 화면 하단에서 한 번씩 솟아올랐다 내려간다. ease-in-out으로 부드러운 가속/감속.
const NEMO_TRANSITION = {
  duration: 0.3,
  ease: "easeInOut" as const,
};

// 각 intro phase별 네모 캐릭터 표정 매핑. face(1)는 moved(감동), body(2)는 satisfied(흡족),
// leg(3)는 confused(어리둥절). 6개 motion.div(빨강 3 + 초록 3)이 모두 미리 마운트되어 viewport
// 밑(y=100%)에 대기하다, 자신의 phase일 때만 y=15%로 올라온다. 모든 캐릭터가 항상 DOM에 존재해
// AnimatePresence 기반 src swap에서 발생할 수 있는 렌더링 지연/race condition 없음.
const NEMO_ASSETS_BY_INTRO_PHASE = {
  "intro-1": { red: redNemoMoved, green: greenNemoMoved },
  "intro-2": { red: redNemoSatisfied, green: greenNemoSatisfied },
  "intro-3": { red: redNemoConfused, green: greenNemoConfused },
} as const;

const INTRO_PHASES = ["intro-1", "intro-2", "intro-3"] as const;

type ChoreographyPhase =
  | "intro-1"
  | "intro-2"
  | "intro-3"
  | "settling"
  | "fanning";

const REVEAL_COUNT_BY_PHASE: Record<ChoreographyPhase, number> = {
  "intro-1": 1,
  "intro-2": 2,
  "intro-3": 3,
  settling: 3,
  fanning: 3,
};

// 각 phase에서 카메라가 집중할 part index. (0=face, 1=body, 2=leg, settling/fanning은 정중앙)
const FOCUS_INDEX_BY_PHASE: Record<ChoreographyPhase, number> = {
  "intro-1": 0,
  "intro-2": 1,
  "intro-3": 2,
  settling: 1,
  fanning: 1,
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
  fanning: null,
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

  // 인트로 진행 동안 페이지 스크롤을 일시 잠금. 모바일/태블릿(flex-col-reverse 레이아웃)에선
  // 슬롯+텍스트 합산 높이가 viewport를 넘어 스크롤이 생기고, 사용자가 인트로 도중 스크롤하면
  // 카메라가 mount 시 measure된 viewport center 좌표에 묶여 있어 화면 밖으로 어긋난다.
  // 데스크탑은 lg:h-screen으로 스크롤이 없어 lock해도 시각적 변화 없음 (분기 불필요).
  // 이전 overflow 값을 캡쳐 후 복원해, 외부 모달이 별도로 lock 중인 경우에도 망가지지 않게 한다.
  useEffect(() => {
    if (isFinalState) return;
    const previousBodyOverflow = document.body.style.overflow;
    const previousHtmlOverflow = document.documentElement.style.overflow;
    document.body.style.overflow = "hidden";
    document.documentElement.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousBodyOverflow;
      document.documentElement.style.overflow = previousHtmlOverflow;
    };
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
      onLeftReveal={onLeftReveal}
      className={className}
    />
  );
}

interface ChoreographyTreeProps {
  phase: ChoreographyPhase;
  onPhaseChange: (next: ChoreographyPhase) => void;
  onComplete: () => void;
  // settling 완료 → fanning phase 진입 시점에 호출. 사이드 카드 rotate/opacity 애니메이션과
  // 동시에 부모(RelayBoothView)의 좌측 영역+배경이 페이드인되도록 트리거.
  // FinalState에서도 idempotent 가드(hasRevealedRef) 뒤에서 동일하게 호출되므로 skip/
  // reduced-motion 경로에서도 중복 발화 안전.
  onLeftReveal: () => void;
  className?: string;
}

interface Measurement {
  centerOffset: { x: number; y: number };
  introScale: number;
  fanAngle: number;
}

function ChoreographyTree({
  phase,
  onPhaseChange,
  onComplete,
  onLeftReveal,
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
      // 한 part가 viewport 짧은 변의 ~85%를 차지하도록 scale 계산.
      // height 기준과 width 기준 둘 중 작은 값을 택해 화면 밖으로 넘치지 않게 보장.
      const scaleByHeight =
        (window.innerHeight * VIEWPORT_FILL_RATIO) / PART_SIZE;
      const scaleByWidth =
        (window.innerWidth * VIEWPORT_FILL_RATIO) / PART_SIZE;
      setMeasurement({
        centerOffset: {
          x: window.innerWidth / 2 - (rect.left + rect.width / 2),
          y: window.innerHeight / 2 - (rect.top + rect.height / 2),
        },
        introScale: Math.min(scaleByHeight, scaleByWidth),
        fanAngle:
          window.innerWidth >= FAN_BREAKPOINT_PX
            ? FAN_ANGLE_LG
            : FAN_ANGLE_SM,
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

  // isSettled: 카메라/슬롯이 settled 상태(slot center, scale 1)로 머물러야 하는 phase 통합 플래그.
  //   - settling: 카메라가 슬롯으로 돌아오는 트랜지션이 진행 중.
  //   - fanning: 카메라/슬롯은 이미 안착했고, 사이드 카드만 0° → ±45°로 회전 중.
  const isSettled = phase === "settling" || phase === "fanning";
  const isFanning = phase === "fanning";
  const focusIndex = FOCUS_INDEX_BY_PHASE[phase];
  const revealCount = REVEAL_COUNT_BY_PHASE[phase];

  return (
    <>
      {/* 좌하단(빨강)/우하단(초록) 네모 캐릭터. 6개 motion.div(빨강 3 + 초록 3)이 모두 미리
          마운트되어 viewport 밑(y=100%)에서 대기. 자신의 phase가 활성일 때만 y=15%로 올라오고
          다른 phase일 땐 y=100%로 내려간다. AnimatePresence + key swap 대신 항상 마운트된 상태로
          y만 토글해 React reconciliation/motion 상태 재설정으로 인한 1~2 프레임 지연(특히
          intro-2 satisfied 캐릭터 누락 증상)을 방지. slot ref와 sibling으로 두어 slot-positioner의
          transform이 fixed 좌표계에 영향을 주지 않도록 분리. */}
      {INTRO_PHASES.map((introPhase) => {
        const assets = NEMO_ASSETS_BY_INTRO_PHASE[introPhase];
        const isActive = phase === introPhase;
        return (
          <motion.div
            key={`nemo-red-${introPhase}`}
            aria-hidden
            className="pointer-events-none fixed bottom-0 left-4 z-50 sm:left-8"
            initial={{ y: "100%" }}
            animate={{ y: isActive ? "15%" : "100%" }}
            transition={NEMO_TRANSITION}
          >
            <Image
              src={assets.red}
              alt=""
              className="h-40 w-40 sm:h-56 sm:w-56 lg:h-75 lg:w-75"
            />
          </motion.div>
        );
      })}

      {INTRO_PHASES.map((introPhase) => {
        const assets = NEMO_ASSETS_BY_INTRO_PHASE[introPhase];
        const isActive = phase === introPhase;
        return (
          <motion.div
            key={`nemo-green-${introPhase}`}
            aria-hidden
            className="pointer-events-none fixed bottom-0 right-4 z-50 sm:right-8"
            initial={{ y: "100%" }}
            animate={{ y: isActive ? "15%" : "100%" }}
            transition={NEMO_TRANSITION}
          >
            <Image
              src={assets.green}
              alt=""
              className="h-40 w-40 sm:h-56 sm:w-56 lg:h-75 lg:w-75"
            />
          </motion.div>
        );
      })}

      <div
        ref={slotRef}
        className={cn("relative", className)}
      // motion 트리가 마운트되기 전에도 slot ref가 실제 아트워크 카드와 동일한 dimension을
      // 갖도록 명시 사이즈를 부여. 빈 div(0×0) 상태에서 measurement가 일어나면 centerOffset이
      // slot 중심이 아닌 상단을 기준으로 계산되어 카메라가 ARTWORK_HEIGHT/2(229px)만큼
      // 아래로 어긋난다 (col-reverse 레이아웃에서만 발생: 데스크탑은 슬롯이 viewport 세로
      // 중앙에 위치해 height=0이든 정확하든 centerOffset.y가 0으로 동일하게 떨어지기 때문).
      style={{ width: PART_SIZE, height: ARTWORK_HEIGHT }}
    >
      {measurement !== null && (
        <motion.div
          // Layer 1 — slot-positioner: viewport center ↔ slot center translate.
          // 인트로 동안엔 viewport center에 정지, settling/fanning에서 slot center로 spring 후 정지.
          className="relative"
          initial={{
            x: measurement.centerOffset.x,
            y: measurement.centerOffset.y,
          }}
          animate={
            isSettled
              ? { x: 0, y: 0 }
              : {
                  x: measurement.centerOffset.x,
                  y: measurement.centerOffset.y,
                }
          }
          transition={SLOT_TRANSITION}
          onAnimationComplete={() => {
            // settling 애니메이션이 안착하면 fanning phase로 전환 → 사이드 카드 rotate+opacity 시작.
            // 동시에 onLeftReveal()로 부모의 배경+좌측 텍스트 페이드인 트리거 → 사이드 카드
            // 펼침 애니메이션과 배경 등장이 같은 시간 창에서 진행된다.
            // fanning phase에서는 slot-positioner target이 그대로(0,0) 유지되어 추가 애니메이션 없음.
            if (phase === "settling") {
              onPhaseChange("fanning");
              onLeftReveal();
            }
          }}
        >
          {/* z-10 BACK — opacity 0으로 마운트되어 인트로/settling 동안 완전히 숨음 (z-30 카메라 카드의
              반투명 영역으로도 비치지 않음). fanning phase에서 opacity 0→1, rotate 0°→+45°가 동시에
              애니메이트되어 부채꼴로 펼쳐지면서 나타난다. 이 카드의 애니메이션 완료가
              전체 시퀀스의 마지막 트리거. */}
          <motion.div
            className="absolute inset-0 z-10 origin-bottom"
            initial={{ rotate: 0, opacity: 0 }}
            animate={{
              rotate: isFanning ? measurement.fanAngle : 0,
              opacity: isFanning ? 1 : 0,
            }}
            transition={SIDE_ROTATION_TRANSITION}
            onAnimationComplete={() => {
              if (isFanning) onComplete();
            }}
          >
            <RelayArtworkCard size={PART_SIZE} />
          </motion.div>

          {/* z-20 MID — 동일 패턴, fanning에서 0° → -fanAngle. onComplete는 z-10에서 처리하므로 여기엔 없음. */}
          <motion.div
            className="absolute inset-0 z-20 origin-bottom"
            initial={{ rotate: 0, opacity: 0 }}
            animate={{
              rotate: isFanning ? -measurement.fanAngle : 0,
              opacity: isFanning ? 1 : 0,
            }}
            transition={SIDE_ROTATION_TRANSITION}
          >
            <RelayArtworkCard size={PART_SIZE} />
          </motion.div>

          {/* Layer 2 — 카메라: 인트로 동안 scale=introScale, translateY=focus offset.
              settling/fanning 시 scale=1, translateY=0으로 줌아웃 후 정지. transform-origin은
              default 50% 50%이라 스케일이 슬롯 중앙을 기준으로 적용되고, translateY로 focus part가
              viewport 중앙에 온다. */}
          <motion.div
            className="relative z-30"
            initial={{
              scale: measurement.introScale,
              y: PART_STEP * 1 * measurement.introScale,
            }}
            animate={
              isSettled
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
    </>
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
      {/* 모바일/태블릿(< lg)은 30°, 데스크탑은 45°. ChoreographyTree의 fanAngle 분기와 동일 1024px. */}
      <div className="absolute inset-0 z-10 origin-bottom rotate-30 lg:rotate-45">
        <RelayArtworkCard size={PART_SIZE} />
      </div>
      <div className="absolute inset-0 z-20 origin-bottom rotate-[-30deg] lg:-rotate-45">
        <RelayArtworkCard size={PART_SIZE} />
      </div>
      <div className="relative z-30">
        <RelayArtworkCard size={PART_SIZE} />
      </div>
    </div>
  );
}
