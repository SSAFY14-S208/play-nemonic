"use client";

import { Fragment } from "react";

import { cn } from "@/shared/libs";

import { useFortuneReducedMotion, useFortuneTypewriterText } from "../hooks";

interface FortuneDialoguePanelProps {
  dialogueIndex: number;
  withEntryReveal?: boolean;
  onNext: () => void;
}

interface FortuneDialogueTextSegment {
  text: string;
  accent?: boolean;
}

type FortuneDialogueLine = FortuneDialogueTextSegment[];

interface FortuneDialogue {
  lines: FortuneDialogueLine[];
  actionLabel: string;
}

const INTRO_DIALOGUE_TYPEWRITER_START_DELAY_MS = 1120;
const NEXT_DIALOGUE_TYPEWRITER_START_DELAY_MS = 120;

export const FORTUNE_DIALOGUES: FortuneDialogue[] = [
  {
    lines: [
      [
        { text: "안녕 난 " },
        { text: "네모닉", accent: true },
        { text: " 마법사 포포!" },
      ],
      [{ text: "오늘의 운세 메모를 뽑아줄게~" }],
    ],
    actionLabel: "다음 이야기 듣기",
  },
  {
    lines: [
      [{ text: "사주에 기반한 멋진 운세를 뽑기 위해서," }],
      [{ text: "정보를 입력해줘!" }],
    ],
    actionLabel: "정보 입력하기",
  },
];

export const DIALOGUE_PANEL_CLASS = cn(
  "relative grid items-end mx-auto border-0 text-left",
  "grid-cols-[minmax(0,1fr)_auto]",
  "w-[94vw] max-w-[980px]",
  "aspect-[1631/502]",
  "gap-[0.55rem]",
  // 패널 이미지가 좌우 비대칭(좌측 장식이 더 큼)이라 pl > pr.
  "pt-[4.5rem] pr-[3.8rem] pb-[1.5rem] pl-[5.6rem]",
  "bg-[url('/images/fortune/stage/dialogue-panel.png')] bg-center bg-no-repeat bg-[length:100%_100%]",
  "[filter:drop-shadow(0_-0.5rem_1.3rem_rgba(79,31,120,0.24))_drop-shadow(0_0.7rem_1.35rem_rgba(6,1,17,0.46))]",
  // mobile overrides (max-[800px]:)
  "max-[800px]:w-[94vw] max-[800px]:max-w-[34rem] max-[800px]:gap-[0.36rem]",
  "max-[800px]:pt-[3.4rem] max-[800px]:pr-[2rem] max-[800px]:pb-[calc(1.1rem+env(safe-area-inset-bottom))] max-[800px]:pl-[3.2rem]",
);

export const DIALOGUE_SPEAKER_CLASS = cn(
  "absolute top-[6.3%] left-[11.1%] grid items-center justify-items-start",
  "w-[29.8%] h-[21%] min-w-[7rem] max-w-[18rem]",
  "m-0 transform-none border-0 bg-transparent text-left",
  "pt-0 pr-[0.7rem] pb-0 pl-[1.4rem]",
  "font-[var(--font-paperlogy)] font-extrabold leading-none",
  "text-[1.4rem] text-[rgba(255,244,216,0.96)]",
  "[text-shadow:0_0.12rem_0.32rem_rgba(8,1,18,0.72),0_0_0.72rem_rgba(193,107,255,0.42)]",
  // mobile overrides — 단순 rem 값으로 분기
  "max-[800px]:top-[6%] max-[800px]:left-[11.2%] max-[800px]:w-[30%] max-[800px]:min-w-[5.3rem]",
  "max-[800px]:pl-[0.95rem] max-[800px]:text-[0.7rem]",
);

export const DIALOGUE_COPY_CLASS = cn(
  "m-0 self-center",
  "font-fortune-hand font-normal leading-[1.42] tracking-normal",
  "text-[1.4rem] text-[rgba(255,249,228,0.98)]",
  "[text-shadow:0_0.16rem_0.45rem_rgba(7,1,16,0.72),0_0_0.86rem_rgba(160,87,255,0.3)]",
  "whitespace-pre-wrap break-keep",
  // mobile overrides
  "max-[800px]:text-[0.7rem] max-[800px]:leading-[1.0]",
);

export const DIALOGUE_NEXT_CLASS = cn(
  "relative self-center p-0 border-0 cursor-pointer text-transparent font-inherit min-h-0",
  "w-[3.4rem] aspect-square",
  "mr-[1.8rem]",
  "bg-transparent bg-[url('/images/fortune/stage/dialogue-next-button.png')] bg-center bg-no-repeat bg-contain",
  "[filter:drop-shadow(0_0_0.64rem_rgba(188,104,255,0.5))_drop-shadow(0_0.45rem_0.7rem_rgba(9,1,18,0.4))]",
  "[transition:transform_180ms_ease,filter_180ms_ease]",
  "hover:[filter:drop-shadow(0_0_0.9rem_rgba(213,144,255,0.76))_drop-shadow(0_0.48rem_0.76rem_rgba(9,1,18,0.44))_saturate(1.1)_brightness(1.06)]",
  "hover:translate-x-[0.12rem] hover:scale-[1.04]",
  "focus-visible:outline focus-visible:outline-2 focus-visible:outline-[rgba(255,235,179,0.95)] focus-visible:outline-offset-[0.2rem]",
  // mobile overrides
  "max-[800px]:col-auto max-[800px]:justify-self-end",
  "max-[800px]:w-[2.6rem] max-[800px]:mr-[0.4rem]",
);

export default function FortuneDialoguePanel({
  dialogueIndex,
  withEntryReveal = false,
  onNext,
}: FortuneDialoguePanelProps) {
  const dialogue = FORTUNE_DIALOGUES[dialogueIndex] ?? FORTUNE_DIALOGUES[0];
  const startDelayMs =
    dialogueIndex === 0
      ? INTRO_DIALOGUE_TYPEWRITER_START_DELAY_MS
      : NEXT_DIALOGUE_TYPEWRITER_START_DELAY_MS;

  return (
    <FortuneDialoguePanelContent
      key={dialogueIndex}
      dialogue={dialogue}
      startDelayMs={startDelayMs}
      withEntryReveal={withEntryReveal}
      onNext={onNext}
    />
  );
}

function FortuneDialoguePanelContent({
  dialogue,
  startDelayMs,
  withEntryReveal,
  onNext,
}: {
  dialogue: FortuneDialogue;
  startDelayMs: number;
  withEntryReveal: boolean;
  onNext: () => void;
}) {
  const prefersReducedMotion = useFortuneReducedMotion();
  const characterCount = getDialogueCharacterCount(dialogue);
  const plainText = getDialoguePlainText(dialogue);
  const { completeText, isComplete, visibleCharacterCount } =
    useFortuneTypewriterText({
      characterCount,
      prefersReducedMotion,
      startDelayMs,
    });
  const buttonLabel = isComplete ? dialogue.actionLabel : "대사 모두 표시하기";

  const handleNext = () => {
    if (!isComplete) {
      completeText();
      return;
    }

    onNext();
  };

  return (
    <section
      className={cn(
        DIALOGUE_PANEL_CLASS,
        withEntryReveal &&
          "animate-fortune-dialogue-entry-reveal motion-reduce:animate-none",
      )}
      aria-label="포포의 안내"
    >
      <p className={DIALOGUE_SPEAKER_CLASS}>포포</p>
      <p className={DIALOGUE_COPY_CLASS} aria-label={plainText}>
        {renderDialogueLines(dialogue, visibleCharacterCount)}
        {!isComplete && <FortuneDialogueCaret />}
      </p>
      <button
        type="button"
        className={DIALOGUE_NEXT_CLASS}
        aria-label={buttonLabel}
        onClick={handleNext}
      >
        <span className="sr-only">{buttonLabel}</span>
      </button>
    </section>
  );
}

export function FortuneDialogueCaret() {
  return (
    <span
      aria-hidden
      className="ml-[0.08em] inline-block h-[1em] w-[0.12em] translate-y-[0.16em] rounded-full bg-[rgba(255,242,205,0.92)] align-baseline shadow-[0_0_0.48rem_rgba(206,129,255,0.56)] animate-fortune-dialogue-caret-blink motion-reduce:animate-none"
    />
  );
}

export const FORTUNE_DIALOGUE_GLYPH_CLASS =
  "inline-block animate-fortune-dialogue-glyph-tap origin-[50%_82%] motion-reduce:animate-none";

function getDialogueCharacterCount(dialogue: FortuneDialogue) {
  return dialogue.lines.reduce((totalCharacterCount, line) => {
    const lineCharacterCount = line.reduce((lineTotal, segment) => {
      return lineTotal + Array.from(segment.text).length;
    }, 0);

    return totalCharacterCount + lineCharacterCount;
  }, 0);
}

function getDialoguePlainText(dialogue: FortuneDialogue) {
  return dialogue.lines
    .map((line) => line.map((segment) => segment.text).join(""))
    .join("\n");
}

function renderDialogueLines(
  dialogue: FortuneDialogue,
  visibleCharacterCount: number,
) {
  let remainingCharacterCount = visibleCharacterCount;

  return dialogue.lines.map((line, lineIndex) => {
    const lineContent = line.map((segment, segmentIndex) => {
      const segmentCharacters = Array.from(segment.text);
      const visibleSegmentCharacterCount = Math.min(
        remainingCharacterCount,
        segmentCharacters.length,
      );

      remainingCharacterCount = Math.max(
        remainingCharacterCount - segmentCharacters.length,
        0,
      );

      if (visibleSegmentCharacterCount <= 0) {
        return null;
      }

      const visibleSegmentCharacters = segmentCharacters.slice(
        0,
        visibleSegmentCharacterCount,
      );

      return (
        <span
          key={`${lineIndex}-${segmentIndex}`}
          className={segment.accent ? "text-fortune-accent" : undefined}
        >
          {visibleSegmentCharacters.map((character, characterIndex) => {
            if (character === " ") {
              return character;
            }

            return (
              <span
                key={`${lineIndex}-${segmentIndex}-${characterIndex}`}
                className={FORTUNE_DIALOGUE_GLYPH_CLASS}
              >
                {character}
              </span>
            );
          })}
        </span>
      );
    });

    return (
      <Fragment key={lineIndex}>
        {lineIndex > 0 && <br />}
        {lineContent}
      </Fragment>
    );
  });
}
