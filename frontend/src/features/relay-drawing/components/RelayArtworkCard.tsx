"use client";

import { motion, type Variants } from "motion/react";

import { cn } from "@/shared/libs";

import {
  drawing1Body,
  drawing1Face,
  drawing1Leg,
  drawing2Body,
  drawing2Face,
  drawing2Leg,
  drawing3Body,
  drawing3Face,
  drawing3Leg,
} from "@/features/relay-drawing/assets";

import RelayLabelCard from "./RelayLabelCard";

const DRAWING_SETS = {
  1: [
    { src: drawing1Face, alt: "얼굴 그림 1" },
    { src: drawing1Body, alt: "몸통 그림 1" },
    { src: drawing1Leg, alt: "다리 그림 1" },
  ],
  2: [
    { src: drawing2Face, alt: "얼굴 그림 2" },
    { src: drawing2Body, alt: "몸통 그림 2" },
    { src: drawing2Leg, alt: "다리 그림 2" },
  ],
  3: [
    { src: drawing3Face, alt: "얼굴 그림 3" },
    { src: drawing3Body, alt: "몸통 그림 3" },
    { src: drawing3Leg, alt: "다리 그림 3" },
  ],
} as const;

// 위에서 살짝 큰 채로 내려와 안착하는 "챡 달라붙는" spring.
const cardVariants: Variants = {
  hidden: { opacity: 0, scale: 1.18, y: -12 },
  visible: {
    opacity: 1,
    scale: 1,
    y: 0,
    transition: {
      type: "spring",
      stiffness: 320,
      damping: 16,
      mass: 0.9,
      opacity: { duration: 0.18 },
    },
  },
};

interface RelayArtworkCardProps {
  // 1·2·3 중 하나를 선택해 drawing-{n} 이미지 세트를 렌더. 기본값 1.
  variant?: 1 | 2 | 3;
  // 표시할 part 개수. undefined면 모든 part를 즉시 visible 상태로 렌더 (FinalState · 사이드 사본).
  // 0..3 사이 값은 부모가 phase에 맞춰 controlled stagger로 증가시키며, 그때마다 다음 part가
  // spring으로 등장한다.
  revealCount?: number;
  // 각 part의 hidden → visible spring이 안착할 때 1회 호출. 부모는 이 콜백을 phase chain
  // trigger로 사용한다. revealCount가 undefined면(즉시 visible 경로) 호출되지 않는다.
  onPartReveal?: (revealedIndex: number) => void;
  // 라벨지 한 장의 px 폭. 높이는 3:2 비율로 자동 결정.
  // 인트로 애니메이션처럼 수치 계산이 필요한 곳에서만 명시.
  // 생략하면 RelayLabelCard의 Tailwind 반응형 클래스가 적용됨.
  size?: number;
  className?: string;
}

export default function RelayArtworkCard({
  variant = 1,
  revealCount,
  onPartReveal,
  size,
  className,
}: RelayArtworkCardProps) {
  const isControlled = revealCount !== undefined;
  const parts = DRAWING_SETS[variant];
  return (
    <div className={cn("relative flex flex-col gap-1 items-center", className)}>
      {parts.map((part, index) => {
        const isVisible = !isControlled || index < (revealCount ?? 0);
        return (
          <motion.div
            key={part.alt}
            variants={cardVariants}
            initial={isControlled ? "hidden" : "visible"}
            animate={isVisible ? "visible" : "hidden"}
            onAnimationComplete={(definition) => {
              if (definition === "visible" && isControlled) {
                onPartReveal?.(index);
              }
            }}
          >
            <RelayLabelCard imageSrc={part.src} imageAlt={part.alt} size={size} />
          </motion.div>
        );
      })}
    </div>
  );
}
