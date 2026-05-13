"use client";

import { useState, useEffect } from "react";
import Image from "next/image";
import Link from "next/link";
import { MousePointerClick } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { cn } from "@/shared/libs";
import nemonicIcon from "./assets/icons/nemonic-icon.svg";
import useLandingProgress from "./useLandingProgress";

const DOT_INTERVAL = 500;

export default function LandingWelcomeOverlay() {
  const { phase, progress, dismissWelcome } = useLandingProgress();
  const [dotCount, setDotCount] = useState(1);

  // Animated dots: . → .. → ...
  useEffect(() => {
    if (phase !== "loading") return;
    const interval = setInterval(() => {
      setDotCount((previous) => (previous % 3) + 1);
    }, DOT_INTERVAL);
    return () => clearInterval(interval);
  }, [phase]);

  const isLoadingScreen = phase === "loading" || phase === "ready";

  return (
    <>
      {/* Loading Screen */}
      <AnimatePresence>
        {isLoadingScreen && (
          <motion.div
            initial={{ opacity: 1 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.6 }}
            className={cn(
              "fixed inset-0 z-(--z-modal)",
              "flex flex-col items-center justify-center gap-8",
              "bg-black",
            )}
          >
            <p className="h2-b text-fg-inverse">
              {phase === "ready"
                ? "Nemonic이 준비되었습니다!"
                : `Nemonic을 준비중입니다${".".repeat(dotCount)}`}
            </p>

            <div className="flex w-72 flex-col items-center gap-3">
              <span className="h3-b text-fg-inverse">{progress}%</span>
              <div className="h-3 w-full overflow-hidden rounded-full border border-white/30 bg-white/10">
                <motion.div
                  className="h-full rounded-full bg-white"
                  initial={{ width: 0 }}
                  animate={{ width: `${progress}%` }}
                  transition={{ duration: 0.3, ease: "easeInOut" }}
                />
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Welcome Overlay */}
      <AnimatePresence>
        {phase === "welcome" && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.4 }}
            className={cn(
              "fixed inset-0 z-(--z-modal)",
              "flex flex-col items-center justify-center",
              "bg-black/70 backdrop-blur-sm",
            )}
          >
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.2 }}
              className="flex flex-col items-center gap-10"
            >
              <Image
                src={nemonicIcon}
                alt="네모닉 프린터"
                width={140}
                height={140}
                className="invert drop-shadow-[0_0_24px_rgba(255,255,255,0.25)]"
              />

              <div className="flex flex-col items-center gap-3">
                <h2 className="h1-b text-fg-inverse">환영합니다!</h2>
                <div className="flex items-center gap-2 text-fg-inverse">
                  <MousePointerClick className="h-5 w-5 shrink-0 opacity-70" />
                  <p className="body-l-r opacity-70">
                    기기를 클릭해 네모닉 기기를 체험해보세요!
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={dismissWelcome}
                className={cn(
                  "h4-b text-fg-inverse bg-primary-1",
                  "rounded-lg px-10 py-3",
                  "shadow-lg",
                  "hover:opacity-90 transition-opacity",
                )}
              >
                확인
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Hub Entry Button */}
      {phase === "done" && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.5, delay: 0.3 }}
          className={cn(
            "pointer-events-none fixed inset-0 z-(--z-overlay)",
            "flex items-start justify-end p-6",
          )}
        >
          <Link
            href="/hub"
            className={cn(
              "pointer-events-auto",
              "h3-b text-fg-inverse bg-primary-1",
              "rounded-lg px-8 py-4",
              "shadow-lg",
              "hover:opacity-90 transition-opacity",
            )}
          >
            네모닉 월드 입장
          </Link>
        </motion.div>
      )}
    </>
  );
}
