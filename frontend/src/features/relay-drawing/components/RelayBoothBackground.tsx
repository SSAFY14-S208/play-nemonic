"use client";

import { motion } from "motion/react";
import type { CSSProperties } from "react";

import { cn } from "@/shared/libs";

interface RelayBoothBackgroundProps {
  // 인트로 시퀀스가 끝나면(좌측이 페이드 인되는 시점) 배경 데코도 함께 등장.
  isVisible: boolean;
}

// 부스 페이지의 노트 같은 분위기를 채우는 데코 백그라운드 — sparkle, squiggle,
// dot, mini post-it, heart, 물음표 등 작은 요소를 흩뿌린다.
//
// 모든 요소는 `pointer-events-none` + `aria-hidden`으로 상호작용/접근성에서 제외.
// 위치는 % 기반이라 viewport 폭에 따라 자연스럽게 따라온다.
export default function RelayBoothBackground({
  isVisible,
}: RelayBoothBackgroundProps) {
  return (
    <motion.div
      aria-hidden
      className="pointer-events-none absolute inset-0 -z-10 overflow-hidden"
      initial={{ opacity: 0 }}
      animate={{ opacity: isVisible ? 1 : 0 }}
      transition={{ duration: 0.6, ease: "easeOut" }}
    >
      {/* ── Sparkles ───────────────────────────────────────────── */}
      <Sparkle
        className="absolute left-[42%] top-[10%] text-relay-accent"
        size={56}
      />
      <Sparkle
        className="absolute left-[30%] top-[34%] text-relay-coral"
        size={44}
      />
      <Sparkle
        className="absolute left-[88%] top-[42%] text-relay-pink"
        size={32}
      />
      <Sparkle
        className="absolute left-[8%] top-[68%] text-relay-accent-strong"
        size={36}
      />
      <Sparkle
        className="absolute left-[78%] top-[88%] text-relay-card-blue"
        size={40}
      />
      <Sparkle
        className="absolute left-[62%] top-[28%] text-relay-yellow"
        size={28}
      />

      {/* ── Squiggle 라인 ──────────────────────────────────────── */}
      <Squiggle
        className="absolute left-[6%] top-[72%] text-relay-accent rotate-[-8deg]"
        width={140}
      />
      <Squiggle
        className="absolute left-[80%] top-[58%] text-relay-card-blue rotate-6"
        width={170}
      />
      <Squiggle
        className="absolute left-[36%] top-[92%] text-relay-green rotate-[-4deg]"
        width={140}
      />

      {/* ── Color dot 클러스터 ─────────────────────────────────── */}
      <DotCluster className="absolute left-[10%] top-[20%]" />
      <DotCluster
        className="absolute left-[68%] top-[18%]"
        colors={["bg-relay-coral", "bg-relay-yellow", "bg-relay-green"]}
      />

      {/* ── Mini post-it 메모지 ────────────────────────────────── */}
      <MiniPostIt
        className="absolute left-[80%] top-[8%] -rotate-3"
        color="yellow"
      />
      <MiniPostIt
        className="absolute left-[5%] top-[44%] -rotate-6"
        color="blue"
      />
      <MiniPostIt
        className="absolute left-[58%] top-[78%] rotate-[5deg]"
        color="pink"
      />

      {/* ── 텍스트 액센트 (hearts, ?) ──────────────────────────── */}
      <span className="absolute left-[48%] top-[82%] text-6xl text-relay-coral">
        ♡
      </span>
      <span className="absolute left-[14%] top-[82%] text-7xl font-bold text-relay-accent-strong">
        ?
      </span>
      <span className="absolute left-[72%] top-[68%] text-7xl font-bold text-relay-card-blue">
        ?
      </span>

      {/* ── Dotted ellipsis ────────────────────────────────────── */}
      <span className="absolute left-[64%] top-[54%] text-7xl tracking-[0.4em] text-relay-muted">
        ......
      </span>
    </motion.div>
  );
}

// ── primitive 데코 컴포넌트들 (이 파일 내부에서만 사용) ────────────

interface SparkleProps {
  className?: string;
  size?: number;
}

function Sparkle({ className, size = 32 }: SparkleProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      className={cn(className)}
      fill="currentColor"
      aria-hidden
    >
      <path d="M12 0 C12.6 7.5 16.5 11.4 24 12 C16.5 12.6 12.6 16.5 12 24 C11.4 16.5 7.5 12.6 0 12 C7.5 11.4 11.4 7.5 12 0 Z" />
    </svg>
  );
}

interface SquiggleProps {
  className?: string;
  width?: number;
}

function Squiggle({ className, width = 120 }: SquiggleProps) {
  return (
    <svg
      viewBox="0 0 80 16"
      width={width}
      height={(width / 80) * 16}
      className={cn(className)}
      fill="none"
      stroke="currentColor"
      strokeWidth={3}
      strokeLinecap="round"
      aria-hidden
    >
      <path d="M2 8 Q 12 0, 22 8 T 42 8 T 62 8 T 78 8" />
    </svg>
  );
}

interface DotClusterProps {
  className?: string;
  colors?: string[];
}

const DEFAULT_DOT_COLORS = [
  "bg-relay-coral",
  "bg-relay-card-blue",
  "bg-relay-green",
  "bg-relay-pink",
];

function DotCluster({
  className,
  colors = DEFAULT_DOT_COLORS,
}: DotClusterProps) {
  return (
    <div className={cn("flex gap-2", className)}>
      {colors.map((colorClass, dotIndex) => (
        <span
          key={`${colorClass}-${dotIndex}`}
          className={cn("inline-block h-5 w-5 rounded-full", colorClass)}
        />
      ))}
    </div>
  );
}

interface MiniPostItProps {
  className?: string;
  color?: "yellow" | "pink" | "blue";
}

const POST_IT_COLOR_MAP: Record<
  NonNullable<MiniPostItProps["color"]>,
  string
> = {
  yellow: "bg-relay-postit",
  pink: "bg-relay-pink",
  blue: "bg-relay-card-blue/70",
};

function MiniPostIt({ className, color = "yellow" }: MiniPostItProps) {
  return (
    <div
      className={cn(
        "rounded-md p-15 text-center font-bold text-relay-ink shadow-lg",
        POST_IT_COLOR_MAP[color],
        className,
      )}
      style={{ fontSize: "16px", lineHeight: 1.35 } satisfies CSSProperties}
    ></div>
  );
}
