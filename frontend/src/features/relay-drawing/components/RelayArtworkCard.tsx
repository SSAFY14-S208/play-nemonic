"use client";

import { motion, type Variants } from "motion/react";

import { cn } from "@/shared/libs";

import magicianBody from "../assets/magician-body-t.png";
import magicianFace from "../assets/magician-face-t.png";
import magicianLeg from "../assets/magician-leg-t.png";

import RelayLabelCard from "./RelayLabelCard";

// TODO: 추후 face/body/leg 이미지 세트를 variant prop으로 받아 다른 캐릭터 라벨도
// 같은 컴포넌트로 그릴 수 있도록 일반화. 현재는 마술사 단일 세트 하드코딩.
const MAGICIAN_PARTS = [
  { src: magicianFace, alt: "얼굴 라벨" },
  { src: magicianBody, alt: "몸통 라벨" },
  { src: magicianLeg, alt: "다리 라벨" },
] as const;

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
  // 표시할 part 개수. undefined면 모든 part를 즉시 visible 상태로 렌더 (FinalState · 사이드 사본).
  // 0..3 사이 값은 부모가 phase에 맞춰 controlled stagger로 증가시키며, 그때마다 다음 part가
  // spring으로 등장한다.
  revealCount?: number;
  // 각 part의 hidden → visible spring이 안착할 때 1회 호출. 부모는 이 콜백을 phase chain
  // trigger로 사용한다. revealCount가 undefined면(즉시 visible 경로) 호출되지 않는다.
  onPartReveal?: (revealedIndex: number) => void;
  // 라벨지 한 장의 px 사이즈.
  size?: number;
  className?: string;
}

export default function RelayArtworkCard({
  revealCount,
  onPartReveal,
  size = 150,
  className,
}: RelayArtworkCardProps) {
  const isControlled = revealCount !== undefined;
  return (
    <div className={cn("relative flex flex-col gap-1 items-center", className)}>
      {MAGICIAN_PARTS.map((part, index) => {
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
