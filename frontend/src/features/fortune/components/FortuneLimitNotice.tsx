"use client";

import { LogOut, Sparkles } from "lucide-react";
import { Fragment } from "react";

import { cn } from "@/shared/libs";

import { useFortuneSessionStore } from "../fortuneSessionStore";
import { useFortuneReducedMotion, useFortuneTypewriterText } from "../hooks";
import { getNextKoreanMidnightLabel } from "../utils";

import {
  DIALOGUE_COPY_CLASS,
  DIALOGUE_PANEL_CLASS,
  DIALOGUE_SPEAKER_CLASS,
  FORTUNE_DIALOGUE_GLYPH_CLASS,
  FortuneDialogueCaret,
} from "./FortuneDialoguePanel";
import FortuneDrawAction from "./FortuneDrawAction";

interface FortuneLimitNoticeProps {
  onShowResult: () => void;
  onBackToHub: () => void;
}

interface LimitDialogueSegment {
  text: string;
  accent?: boolean;
}

type LimitDialogueLine = LimitDialogueSegment[];

const LIMIT_DIALOGUE_LINES: LimitDialogueLine[] = [
  [
    { text: "이미 " },
    { text: "오늘의 운세", accent: true },
    { text: "를 확인했어!" },
  ],
  [{ text: "내일 다시 찾아와줘~" }],
];

const TYPEWRITER_START_DELAY_MS = 320;

export default function FortuneLimitNotice({
  onShowResult,
  onBackToHub,
}: FortuneLimitNoticeProps) {
  const result = useFortuneSessionStore((state) => state.result);
  const prefersReducedMotion = useFortuneReducedMotion();
  const characterCount = getCharacterCount(LIMIT_DIALOGUE_LINES);
  const plainText = getPlainText(LIMIT_DIALOGUE_LINES);
  const { completeText, isComplete, visibleCharacterCount } =
    useFortuneTypewriterText({
      characterCount,
      prefersReducedMotion,
      startDelayMs: TYPEWRITER_START_DELAY_MS,
    });
  const nextResetLabel = getNextKoreanMidnightLabel();

  const handleAdvance = () => {
    if (!isComplete) {
      completeText();
    }
  };

  return (
    <section
      // DIALOGUE_PANEL_CLASS의 `grid items-end`는 기본 align-content가 stretch라
      // 행이 늘어나서 빈 공간이 행 안에 생김. auto-rows-min + content-center로
      // 행을 콘텐츠 크기에 맞추고 전체 콘텐츠를 패널 가운데로 모음.
      //
      // 패널 이미지가 좌우 비대칭(좌측 장식이 더 큼)이라 기본 pl/pr 만으로는
      // content area center가 패널 visual center(가운데 보석)와 어긋남.
      // 아래 두 값(pl/pr %)만 손쉽게 조절해서 보석 위치에 정렬 맞추면 됨.
      //   - 콘텐츠를 오른쪽으로 더 보내려면 → pl-[%] 늘림 (또는 pr-[%] 줄임)
      //   - 콘텐츠를 왼쪽으로 더 보내려면  → pr-[%] 늘림 (또는 pl-[%] 줄임)
      className={cn(
        DIALOGUE_PANEL_CLASS,
        "gap-0 auto-rows-min content-center",
        "pl-[12.3%] pr-[12%]",
        "max-[800px]:pl-[10%] max-[800px]:pr-[10%] max-[800px]:gap-0",
      )}
      aria-label="포포의 한도 안내"
    >
      <p className={DIALOGUE_SPEAKER_CLASS}>포포</p>
      {/* 대사 — col-span-full로 그리드 2컬럼을 가로지르게 함 + 폰트 축소 */}
      <p
        className={cn(
          DIALOGUE_COPY_CLASS,
          "col-span-full text-center",
          LIMIT_COPY_CLASS,
        )}
        aria-label={plainText}
        onClick={handleAdvance}
      >
        {renderLines(LIMIT_DIALOGUE_LINES, visibleCharacterCount)}
        {!isComplete && <FortuneDialogueCaret />}
      </p>
      {/* 자정 안내 캡션 — 대사 바로 아래, 가운데 정렬 */}
      <p className="col-span-full text-[0.5rem] text-center text-fortune-muted max-[767px]:portrait:text-[0.5rem]">
        {nextResetLabel}
      </p>
      {/* 액션 버튼 두 개 — col-span-full로 그리드 풀고 flex 중앙 정렬 */}
      <div
        className={cn(
          "col-span-full mt-3 flex items-center justify-center",
          "max-[800px]:portrait:mt-0",
          "gap-[clamp(0.6rem,1.4vw,1.8rem)]",
        )}
      >
        <FortuneDrawAction
          tone="edit"
          className={LIMIT_ACTION_CLASS}
          icon={<Sparkles className={LIMIT_ACTION_ICON_CLASS} aria-hidden />}
          disabled={!result}
          onClick={onShowResult}
        >
          운세 확인
        </FortuneDrawAction>
        <FortuneDrawAction
          tone="edit"
          className={LIMIT_ACTION_CLASS}
          icon={<LogOut className={LIMIT_ACTION_ICON_CLASS} aria-hidden />}
          onClick={onBackToHub}
        >
          종료하기
        </FortuneDrawAction>
      </div>
    </section>
  );
}

// ─── 손쉽게 조절할 수 있도록 단순 값으로 정리 ──────────────────────────────
// 각 분기마다 하나의 숫자만 손보면 바로 적용됨.
// (clamp 패턴은 부드러운 viewport 스케일을 위해 쓰이지만, 직접 튜닝하기 어려워
//  여기선 단순 rem/% 값으로 분기 처리.)

// 대사 폰트 — 두 줄 + caption + 버튼이 한 박스에 같이 들어가야 해서 공간 확보 필요
const LIMIT_COPY_CLASS = cn("text-[1.05rem]", "max-[800px]:text-[0.7rem]");

// 액션 버튼 사이즈 — 기본 FortuneDrawAction보다 작게 잡음
const LIMIT_ACTION_CLASS = cn(
  "w-[11rem] px-[0.9rem] text-[0.85rem]",
  "max-[767px]:portrait:w-[7rem] max-[767px]:portrait:px-[0.7rem] max-[767px]:portrait:text-[0.6rem]",
);

const LIMIT_ACTION_ICON_CLASS = cn(
  "w-[1.05rem] h-[1.05rem]",
  "max-[767px]:portrait:w-[0.7rem] max-[767px]:portrait:h-[0.7rem]",
);

function getCharacterCount(lines: LimitDialogueLine[]) {
  return lines.reduce((total, line) => {
    return (
      total +
      line.reduce(
        (lineTotal, segment) => lineTotal + Array.from(segment.text).length,
        0,
      )
    );
  }, 0);
}

function getPlainText(lines: LimitDialogueLine[]) {
  return lines
    .map((line) => line.map((segment) => segment.text).join(""))
    .join("\n");
}

function renderLines(
  lines: LimitDialogueLine[],
  visibleCharacterCount: number,
) {
  let remaining = visibleCharacterCount;

  return lines.map((line, lineIndex) => {
    const lineContent = line.map((segment, segmentIndex) => {
      const segmentCharacters = Array.from(segment.text);
      const visible = Math.min(remaining, segmentCharacters.length);

      remaining = Math.max(remaining - segmentCharacters.length, 0);

      if (visible <= 0) {
        return null;
      }

      const visibleCharacters = segmentCharacters.slice(0, visible);

      return (
        <span
          key={`${lineIndex}-${segmentIndex}`}
          className={segment.accent ? "text-fortune-accent" : undefined}
        >
          {visibleCharacters.map((character, characterIndex) => {
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
