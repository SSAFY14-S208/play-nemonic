"use client";

import "./fortune.css";

import { AnimatePresence, motion } from "motion/react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { useShallow } from "zustand/react/shallow";

import { useResizeFreeze } from "@/shared/hooks";
import { cn } from "@/shared/libs";
import { writeCommunityCanvasHandoffDraft } from "@/shared/utils";

import {
  FORTUNE_DIALOGUES,
  FortuneBackToggle,
  FortuneBgmToggle,
  FortuneBirthForm,
  FortuneDialoguePanel,
  FortuneDrawPanel,
  FortuneEntrySpotlightCover,
  FortuneErrorView,
  FortuneLimitNotice,
  FortuneLoadingView,
  FortuneMagicBackdrop,
  FortunePrintStatus,
  FortuneResultCard,
} from "./components";
import { useFortuneSessionStore } from "./fortuneSessionStore";
import FortuneVisual from "./FortuneVisual";
import {
  useFortuneActions,
  useFortuneAudio,
  useFortuneBgm,
  useFortuneReducedMotion,
  useFortuneSessionHydration,
} from "./hooks";
import { createFortuneCommunityImageDataUrl } from "./utils";

const LAST_DIALOGUE_INDEX = FORTUNE_DIALOGUES.length - 1;

export default function FortunePage() {
  const router = useRouter();
  const [noticeMessage, setNoticeMessage] = useState("");
  const [dialogueIndex, setDialogueIndex] = useState(0);
  const [isEntrySceneReady, setIsEntrySceneReady] = useState(false);

  useFortuneSessionHydration();
  useResizeFreeze();
  const {
    completePrinting,
    editBirthInfo,
    retryAfterError,
    returnToIntro,
    showTodayResult,
    startBirthInfo,
    startPrinting,
    submitBirthInfo,
  } = useFortuneActions();
  const { step, result, hasHydrated } = useFortuneSessionStore(
    useShallow((state) => ({
      step: state.step,
      result: state.result,
      hasHydrated: state.hasHydrated,
    })),
  );
  const { playPrintComplete, playPrintStart, playTap } = useFortuneAudio();
  const { isBgmMuted, toggleFortuneBgmMuted } = useFortuneBgm();
  const prefersReducedMotion = useFortuneReducedMotion();

  const handleDialogueNext = () => {
    if (dialogueIndex < LAST_DIALOGUE_INDEX) {
      setDialogueIndex((currentDialogueIndex) => currentDialogueIndex + 1);
      return;
    }

    startBirthInfo();
    setIsEntrySceneReady(false);
  };

  const handleReturnToDialogue = () => {
    setDialogueIndex(LAST_DIALOGUE_INDEX);
    setIsEntrySceneReady(false);
    returnToIntro();
  };

  const handleStartPrinting = () => {
    playPrintStart();
    void startPrinting();
  };

  const handlePrintComplete = () => {
    playPrintComplete();
    completePrinting();
  };

  const handleEntrySceneReady = useCallback(() => {
    setIsEntrySceneReady(true);
  }, []);

  const handleAttach = () => {
    if (result) {
      const imageUrl =
        result.fortuneImageUrl ?? createFortuneCommunityImageDataUrl(result);
      if (!imageUrl) {
        setNoticeMessage("커뮤니티에 붙일 운세 이미지를 만들지 못했어요.");
        return;
      }

      writeCommunityCanvasHandoffDraft({
        sourceKind: "FORTUNE",
        title: result.title,
        imageUrl,
        thumbnailUrl: imageUrl,
        sourceContentKind: "fortune",
      });
      router.push("/community-canvas");
      return;
    }

    setNoticeMessage("커뮤니티에 붙일 운세 결과가 없어요.");
  };

  const goBackToHub = () => {
    router.push("/");
  };

  const shouldPrepareEntrySpotlight = step === "intro" && dialogueIndex === 0;
  const shouldPlayEntrySpotlight =
    shouldPrepareEntrySpotlight && isEntrySceneReady;

  useEffect(() => {
    if (!shouldPrepareEntrySpotlight || isEntrySceneReady) {
      return;
    }

    let cancelled = false;

    (async () => {
      await new Promise((resolve) =>
        window.setTimeout(resolve, ENTRY_SCENE_READY_FALLBACK_DELAY_MS),
      );

      if (!cancelled) {
        setIsEntrySceneReady(true);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [isEntrySceneReady, shouldPrepareEntrySpotlight]);

  return (
    <main
      className="isolate relative min-h-dvh overflow-hidden bg-fortune-backdrop text-fortune-ink"
      onPointerDown={(event) => {
        const target = event.target as Element | null;
        if (!target) return;
        const interactive = target.closest(
          'button, [role="button"], summary, label[data-fortune-tap-target]',
        );
        if (interactive && !(interactive as HTMLButtonElement).disabled) {
          playTap();
        }
      }}
    >
      <FortuneMagicBackdrop />
      <FortuneVisual
        playEntrySpotlight={shouldPrepareEntrySpotlight}
        onEntrySceneReady={handleEntrySceneReady}
        onPrintComplete={handlePrintComplete}
      />
      <section
        className={cn(
          "relative z-5 flex min-h-dvh flex-col justify-end p-[clamp(1rem,3vw,2.2rem)] pointer-events-none max-[800px]:p-[0.9rem]",
          step === "birthInfo" &&
            "justify-center bg-[radial-gradient(ellipse_at_50%_48%,rgba(24,8,37,0.1),rgba(24,8,37,0)_58%),linear-gradient(180deg,rgba(15,4,25,0.08),rgba(15,4,25,0.18))] backdrop-blur-[1.5px]",
          (step === "intro" || step === "limit") &&
            "justify-end px-[clamp(0.45rem,1.8vw,1.25rem)] pt-0 pb-[clamp(0.2rem,1vh,0.75rem)] max-[800px]:px-0 max-[800px]:pb-[clamp(0.2rem,1.2vh,0.55rem)]",
          step === "draw" && "p-0 [&>div]:transform-none!",
          ((step === "printing" && prefersReducedMotion) || step === "error") &&
            "items-center pb-[clamp(2rem,6.4vh,4.1rem)]",
          step === "result" &&
            // 데스크탑은 위/아래 40px, 모바일(≤800px)은 좌상단 월드 링크 + 우상단 음악 토글이
            // 결과 카드를 가리지 않도록 상단을 88px(pt-22)으로 더 밀어줍니다.
            "items-center h-dvh justify-start overflow-y-auto pt-10 pb-10 max-[800px]:pt-22 max-[800px]:pb-6 pointer-events-auto overscroll-contain",
        )}
      >
        <AnimatePresence mode="sync">
          <motion.div
            className={cn(
              "w-full pointer-events-none *:pointer-events-auto",
              (step === "intro" || step === "limit") && "flex items-end",
              ((step === "printing" && prefersReducedMotion) ||
                step === "error") &&
                "max-w-[min(90vw,600px)]",
              step === "result" && "max-w-[min(94vw,920px)]",
            )}
            key={`${hasHydrated}-${step}`}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{
              opacity: 0,
              y: -8,
              scale: 0.992,
              transition: { duration: 0.08, ease: "easeOut" },
            }}
            initial={{ opacity: 0, y: 18, scale: 0.985 }}
            transition={{ duration: 0.26, ease: "easeOut" }}
          >
            {renderFortuneStep()}
          </motion.div>
        </AnimatePresence>
        {noticeMessage && (
          <p
            className="body-r mx-auto mt-4 max-w-[720px] rounded-[var(--radius-md)] bg-fortune-glow px-4 py-3 text-fortune-accent-strong"
            aria-live="polite"
          >
            {noticeMessage}
          </p>
        )}
      </section>
      {shouldPrepareEntrySpotlight && (
        <FortuneEntrySpotlightCover isLit={shouldPlayEntrySpotlight} />
      )}
      {step !== "birthInfo" && (
        <FortuneBgmToggle
          isMuted={isBgmMuted}
          onToggle={toggleFortuneBgmMuted}
        />
      )}
    </main>
  );

  function renderFortuneStep() {
    if (!hasHydrated) {
      return <FortuneLoadingView />;
    }

    if (step === "intro") {
      return (
        <FortuneDialoguePanel
          dialogueIndex={dialogueIndex}
          withEntryReveal={shouldPlayEntrySpotlight}
          onNext={handleDialogueNext}
        />
      );
    }

    if (step === "birthInfo") {
      return (
        <div className="mx-auto grid w-[min(91vw,27rem)] gap-2 max-[800px]:w-[min(94vw,27rem)] max-[800px]:gap-2">
          <div className="flex w-full items-center justify-between gap-3">
            <FortuneBackToggle onClick={handleReturnToDialogue} inline />
            <FortuneBgmToggle
              isMuted={isBgmMuted}
              onToggle={toggleFortuneBgmMuted}
              inline
            />
          </div>
          <FortuneBirthForm onSubmit={submitBirthInfo} />
        </div>
      );
    }

    if (step === "draw") {
      return (
        <FortuneDrawPanel onDraw={handleStartPrinting} onEdit={editBirthInfo} />
      );
    }

    if (step === "printing") {
      return prefersReducedMotion ? <FortunePrintStatus /> : null;
    }

    if (step === "result" && result) {
      return (
        <FortuneResultCard onAttach={handleAttach} onBackToHub={goBackToHub} />
      );
    }

    if (step === "limit") {
      return (
        <FortuneLimitNotice
          onShowResult={showTodayResult}
          onBackToHub={goBackToHub}
        />
      );
    }

    return <FortuneErrorView onRetry={retryAfterError} />;
  }
}

const ENTRY_SCENE_READY_FALLBACK_DELAY_MS = 900;
