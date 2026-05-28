"use client";

import { AnimatePresence, motion } from "motion/react";

interface PartTimeUpOverlayProps {
  isVisible: boolean;
  // 마지막 라운드(legs)의 PART_TIME_UP은 다음 PART_STARTED 대신
  // ALL_PARTS_COMPLETED → FINISHED로 이어지므로 다른 카피를 보여준다.
  isLastRound: boolean;
}

export default function PartTimeUpOverlay({
  isVisible,
  isLastRound,
}: PartTimeUpOverlayProps) {
  const message = isLastRound
    ? "결과를 만드는 중..."
    : "다음 파트를 준비 중...";

  return (
    <AnimatePresence>
      {isVisible && (
        <motion.div
          key="part-time-up-overlay"
          className="fixed inset-0 z-[var(--z-overlay)] grid place-items-center bg-black/70"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2, ease: "easeOut" }}
          role="status"
          aria-live="polite"
        >
          <div className="flex flex-col items-center gap-4 text-relay-paper">
            <span
              aria-hidden
              className="size-12 animate-spin rounded-full border-4 border-relay-paper/30 border-t-relay-accent"
            />
            <p className="body-l-r">{message}</p>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
