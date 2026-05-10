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

const STAGGER_INTERVAL_SECONDS = 0.5;

const containerVariants: Variants = {
  hidden: {},
  visible: { transition: { staggerChildren: STAGGER_INTERVAL_SECONDS } },
};

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
  // true면 mount 시점에 face → body → leg 순서로 stagger 조립 애니메이션 재생.
  // false면 즉시 완성 상태로 표시 (배경에 깔리는 사본용).
  playAssembly?: boolean;
  // 모든 자식 카드의 spring이 안착 완료된 시점에 1회 호출.
  onAssemblyComplete?: () => void;
  // 라벨지 한 장의 px 사이즈. 부스 인트로 choreography에선 200, 최종 상태에선 100.
  size?: number;
  className?: string;
}

export default function RelayArtworkCard({
  playAssembly = true,
  onAssemblyComplete,
  size = 200,
  className,
}: RelayArtworkCardProps) {
  return (
    <motion.div
      className={cn("relative flex flex-col gap-1 items-center", className)}
      variants={containerVariants}
      initial={playAssembly ? "hidden" : "visible"}
      animate="visible"
      onAnimationComplete={() => {
        if (playAssembly) onAssemblyComplete?.();
      }}
    >
      {MAGICIAN_PARTS.map((part) => (
        <motion.div key={part.alt} variants={cardVariants}>
          <RelayLabelCard imageSrc={part.src} imageAlt={part.alt} size={size} />
        </motion.div>
      ))}
    </motion.div>
  );
}
