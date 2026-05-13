"use client";

import { motion } from "motion/react";
import Image from "next/image";

import relayDrawingBg from "../assets/relay-drawing-bg.png";

interface RelayBoothBackgroundProps {
  // 인트로 시퀀스가 끝나면(좌측이 페이드 인되는 시점) 배경도 함께 등장.
  isVisible: boolean;
}

// 부스 페이지의 일러스트 배경. 인트로 시퀀스가 완료된 시점에 페이드인된다.
// 장식 이미지이므로 alt="" + aria-hidden, pointer-events-none으로 상호작용/접근성 제외.
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
      <Image
        src={relayDrawingBg}
        alt=""
        fill
        priority
        sizes="100vw"
        style={{ objectFit: "cover" }}
      />
    </motion.div>
  );
}
