"use client";

import { motion } from "motion/react";
import { cn } from "@/shared/libs";

const MOUSE_BOUNCE_TRANSITION = {
  duration: 1.4,
  ease: "easeInOut",
  repeat: Infinity,
} as const;

const WHEEL_SCROLL_TRANSITION = {
  duration: 1.15,
  ease: "easeInOut",
  repeat: Infinity,
} as const;

export default function LandingScrollHint() {
  return (
    <div
      aria-hidden="true"
      className={cn(
        "pointer-events-none fixed right-6 top-1/2 z-20",
        "flex -translate-y-1/2 flex-col items-center gap-3",
      )}
    >
      <motion.div
        animate={{ opacity: [0.62, 1, 0.62], y: [0, 7, 0] }}
        className={cn(
          "relative h-16 w-10 rounded-full",
          "border-2 border-fg-primary/65 bg-surface-default/45",
          "shadow-[0_8px_24px_rgba(0,0,0,0.12)] backdrop-blur-[2px]",
        )}
        transition={MOUSE_BOUNCE_TRANSITION}
      >
        <motion.span
          className={cn(
            "absolute left-1/2 top-3 h-3 w-1 -translate-x-1/2",
            "rounded-full bg-fg-primary/75",
          )}
          animate={{ opacity: [1, 0.35, 1], y: [0, 13, 0] }}
          transition={WHEEL_SCROLL_TRANSITION}
        />
      </motion.div>
      <motion.div
        animate={{ opacity: [0.35, 0.8, 0.35], y: [0, 6, 0] }}
        className="h-8 w-px rounded-full bg-fg-primary/45"
        transition={MOUSE_BOUNCE_TRANSITION}
      />
    </div>
  );
}
