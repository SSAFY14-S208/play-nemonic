"use client";

/* eslint-disable @next/next/no-img-element */

import { Feather, Sparkles } from "lucide-react";
import { useMemo } from "react";
import { useShallow } from "zustand/react/shallow";

import { cn } from "@/shared/libs";

import { useFortuneSessionStore } from '..';
import { calculateFortuneSaju, isBirthInfoComplete } from '../utils';import FortuneDrawAction from "./FortuneDrawAction";

interface FortuneDrawPanelProps {
  onDraw: () => void;
  onEdit: () => void;
}

const PILLAR_KEYS: Array<"year" | "month" | "day" | "hour"> = [
  "year",
  "month",
  "day",
  "hour",
];
const PILLAR_LABEL_BY_KEY: Record<(typeof PILLAR_KEYS)[number], string> = {
  year: "년주",
  month: "월주",
  day: "일주",
  hour: "시주",
};

const DRAW_RISE = "animate-fortune-draw-rise motion-reduce:animate-none";
const DRAW_ACTIONS_RISE =
  "animate-fortune-draw-actions-rise motion-reduce:animate-none";

// 스테이지 래퍼 — FortuneVisual의 `.fortune-2d-stage`와 동일한 비율로 스케일.
// 뷰포트 비율이 어떻게 바뀌어도 자식 요소들이 포포의 좌표계에 묶여있도록 함.
const STAGE_LAYER_CLASS = cn(
  "[--fortune-stage-aspect:1.4970684]",
  "absolute top-1/2 left-1/2 z-[5]",
  "w-[max(100vw,calc(100dvh*var(--fortune-stage-aspect)))]",
  "h-[max(100dvh,calc(100vw/var(--fortune-stage-aspect)))]",
  "min-w-[100vw] min-h-[100dvh]",
  "[transform:translate3d(-50%,-50%,0)_scale(1.018)]",
  "pointer-events-none *:pointer-events-auto",
  "max-[767px]:portrait:top-[48%]",
  "max-[767px]:portrait:w-[220vw]",
  "max-[767px]:portrait:h-auto",
  "max-[767px]:portrait:min-w-0 max-[767px]:portrait:min-h-0",
  "max-[767px]:portrait:[aspect-ratio:var(--fortune-stage-aspect)]",
);

// 아래 % 값들은 모두 스테이지 기준 좌표 (즉, 포포 기준).
//
// 말풍선 — 포포의 왼쪽 어깨 옆에 위치. 새 에셋(800×200)은 텍스트가 비어 있어
// React에서 직접 대사를 오버레이해야 함. 우측 상단에 "포포" 라벨 + 모자가 박혀
// 있으니 텍스트 영역은 박스의 좌측 ~65% 정도만 사용.
//
// 패널과 동일하게 부품 단위 이동을 위해 wrapper(aspect 고정) + 자식 패턴 사용.
//   - 말풍선 전체 이동/리사이즈 → SPEECH_BUBBLE_WRAPPER_CLASS 수정
//   - 텍스트 위치/사이즈        → SPEECH_BUBBLE_TEXT_CLASS 수정
const SPEECH_BUBBLE_WRAPPER_CLASS = cn(
  "absolute top-[22%] left-[20%] z-[6] w-[27%] aspect-[800/200]",
  // ~810px 이하 portrait — 중간 크기(작은 태블릿/큰 모바일).
  "max-[810px]:portrait:top-[25%] max-[810px]:portrait:left-[24%] max-[810px]:portrait:w-[25%]",
  // ~767px 이하 portrait — 정통 모바일. 스테이지가 220vw로 확장되면서 visible
  // stage 영역이 27~73%(=viewport 100%) 구간으로 좁아짐. wrapper 폭 25% of stage
  // = ~55vw로 컴팩트하게, 좌표는 visible 가운데로 정렬.
  "max-[767px]:portrait:top-[20%] max-[767px]:portrait:left-[28%] max-[767px]:portrait:w-[25%]",
  // ~650px 이하 portrait — 작은 모바일. wrapper 폭을 더 축소해서 캐릭터 가림 최소화.
  "max-[650px]:portrait:left-[28%] max-[650px]:portrait:w-[22%]",
  // ~600px 이하 portrait — 더 작은 모바일. wrapper 살짝 더 줄여서 텍스트 wrap 방지.
  "max-[600px]:portrait:left-[28%] max-[600px]:portrait:w-[24%]",
  "select-none pointer-events-none",
  "[filter:drop-shadow(0_0.6rem_1.2rem_rgba(8,1,22,0.55))]",
  DRAW_RISE,
  "[animation-delay:1620ms]",
);

const SPEECH_BUBBLE_FRAME_CLASS = "absolute inset-0 h-full w-full";

// 대사 텍스트 — 박스 좌측 영역(우상단 "포포" 라벨/모자 영역 제외)에 세로 중앙 정렬.
const SPEECH_BUBBLE_TEXT_CLASS = cn(
  // 텍스트만 박스 내부에서 살짝 아래로 (top-1/2 → top-[60%]로 약 10%p down)
  // 폭은 wrapper의 93% — "포포" 라벨/모자 영역(우측 상단)은 텍스트 윗쪽이라 겹치지 않음
  "absolute left-[4%] top-[60%] -translate-y-1/2 w-[93%]",
  "font-fortune-eulyoo font-medium leading-[1.5] break-keep text-[#f4e8ff]",
  // 데스크탑 폰트 — vw 단위로 wrapper 크기에 비례
  "text-[clamp(0.66rem,0.96vw,1.05rem)] tracking-[0.02em]",
  // ~810px 이하 portrait — 중간 크기. 폰트 더 축소해서 2줄로 들어오도록.
  "max-[810px]:portrait:text-[clamp(0.55rem,1.1vw,0.75rem)]",
  // ~767px 이하 portrait — 정통 모바일. 폰트 더 크게 축소해서 좁은 wrapper 안에
  // "그럼 네모닉에 마법을 걸어..."가 한 줄에 들어오도록.
  "max-[767px]:portrait:text-[clamp(0.55rem,2.4vw,0.78rem)]",
  // ~650px 이하 portrait — 작은 모바일. 더 작은 wrapper에 맞춰 폰트 추가 축소.
  "max-[650px]:portrait:text-[clamp(0.45rem,2vw,0.63rem)]",
  // ~600px 이하 portrait — 텍스트가 한 줄에 들어오도록 폰트 추가 축소.
  "max-[600px]:portrait:text-[clamp(0.42rem,1.6vw,0.58rem)]",
  "[text-shadow:0_0_0.32rem_rgba(120,80,200,0.55),0_0_0.12rem_rgba(255,255,255,0.4)]",
);

// "네모닉" 브랜드 명에 적용할 황금 그라데이션.
// `bg-clip-text + text-transparent` 트릭으로 텍스트에 그라데이션을 입히고,
// 텍스트 자체는 투명이라 기존 text-shadow를 제거(아래로 흘러내림 방지).
const SPEECH_BUBBLE_NEMONIC_CLASS = cn(
  "font-fortune-serif font-bold",
  "bg-gradient-to-b from-[#fff6d0] via-[#f7d97a] to-[#c98f33]",
  "bg-clip-text text-transparent",
  "[text-shadow:none]",
);

// 입력 정보 확인 패널은 5개의 에셋을 겹쳐서 구성:
//   info-panel       (600×440) — 메인 프레임 박스
//   info-panel-tail  (180×313) — 패널 좌하단에서 큐브로 향하는 꼬리
//   info-title       (350×67)  — 패널 상단에 올라가는 타이틀 plaque
//   info-card        (150×257) — 사주 기둥 카드 (4장 가로 배치)
//   info-card-dash   (77×7)    — 카드 안 라벨과 값 사이의 구분선
//
// ─── 패널을 한 덩어리로 이동시키는 방법 ─────────────────────────────────────
// 모든 부품(꼬리, 프레임, 타이틀, 날짜, 카드)이 아래 WRAPPER의 absolute 자식.
// 각 부품의 top/left 값은 WRAPPER 기준 %이므로, **WRAPPER의 top/right/left/w-
// 값만 바꾸면** 부품 간의 상대 정렬은 그대로 유지된 채로 통째로 이동함.
//
//   - 패널 + 꼬리 전체 이동/리사이즈   → INFO_PANEL_WRAPPER_CLASS 수정
//   - 꼬리가 프레임에 붙는 방식 조정    → INFO_PANEL_TAIL_CLASS 수정
//   - 내부 콘텐츠(타이틀/날짜/카드) 위치 → 해당하는 *_CLASS 수정
// ───────────────────────────────────────────────────────────────────────────
const INFO_PANEL_WRAPPER_CLASS = cn(
  "absolute top-[36%] right-[20%] z-[6] w-[25%] aspect-[600/440]",
  "max-[810px]:portrait:top-[44%] max-[810px]:portrait:right-[28%] max-[810px]:portrait:w-[17%]",
  DRAW_RISE,
  "[animation-delay:120ms]",
  "pointer-events-none",
);

const INFO_PANEL_FRAME_CLASS = "absolute inset-0 h-full w-full select-none";

// 꼬리 — 넓은 윗변이 패널의 좌하단 모서리에 붙고, 뾰족한 끝이 좌하단으로 뻗어
// 매직 큐브의 종이 배출구를 향함.
const INFO_PANEL_TAIL_CLASS = cn(
  "absolute top-[35%] left-[-28%] w-[28%] aspect-[180/313] select-none",
  "z-[-1]",
);

// 타이틀 plaque — 패널 상단 장식 영역 안에 자리잡음.
const INFO_TITLE_BOX_CLASS = cn(
  "absolute top-[7%] left-1/2 -translate-x-1/2 w-[54%] aspect-[350/67]",
  "flex items-center justify-center select-none",
);
const INFO_TITLE_BG_CLASS =
  "absolute inset-0 h-full w-full pointer-events-none";
const INFO_TITLE_TEXT_CLASS = cn(
  "relative z-1 font-fortune-eulyoo font-semibold whitespace-nowrap text-center",
  "text-[clamp(0.7rem,1vw,1.05rem)] tracking-[0.06em] text-[#fff8ff]",
  // 600px 이하 portrait — 패널 폭이 좁아져 타이틀이 plaque를 벗어나지 않도록 축소.
  "max-[600px]:portrait:text-[clamp(0.42rem,1.3vw,0.6rem)]",
  "[text-shadow:0_0_0.4rem_rgba(220,170,255,0.7),0_0_0.14rem_rgba(255,255,255,0.5)]",
);

// 날짜 텍스트 — 타이틀 plaque와 카드 행 사이 빈 공간에 배치.
const INFO_DATE_CLASS = cn(
  "absolute top-[30%] left-1/2 -translate-x-1/2 w-[80%] text-center whitespace-nowrap",
  "font-fortune-eulyoo font-semibold text-[clamp(0.78rem,1.25vw,1.35rem)] tracking-[0.04em] text-[#fff8ff]",
  // 600px 이하 portrait — 좁은 패널에 날짜가 잘리지 않도록 축소.
  "max-[600px]:portrait:text-[clamp(0.48rem,1.5vw,0.7rem)]",
  "[text-shadow:0_0_0.5rem_rgba(220,170,255,0.7),0_0_0.18rem_rgba(255,255,255,0.5)]",
);

// 사주 4기둥 카드 행 — 패널 하단 영역에 가로로 배치.
const INFO_CARDS_GRID_CLASS = cn(
  "absolute top-[44%] left-1/2 -translate-x-1/2 w-[86%]",
  "grid grid-cols-4 gap-[2%] m-0 p-0 list-none",
);

const INFO_CARD_CLASS = "relative aspect-[150/257] select-none";
const INFO_CARD_FRAME_CLASS =
  "absolute inset-0 h-full w-full pointer-events-none";
const INFO_CARD_CONTENT_CLASS = cn(
  "absolute inset-0 flex flex-col items-center justify-center",
  "px-[8%] gap-[6%]",
);
const INFO_CARD_LABEL_CLASS = cn(
  "font-fortune-eulyoo font-medium whitespace-nowrap leading-none",
  "text-[clamp(0.55rem,0.9vw,0.95rem)] text-[#e7d4ff] tracking-[0.04em]",
  // 600px 이하 portrait — 카드 안 라벨 축소.
  "max-[600px]:portrait:text-[clamp(0.35rem,1vw,0.5rem)]",
  "[text-shadow:0_0_0.32rem_rgba(220,170,255,0.55)]",
);
const INFO_CARD_VALUE_CLASS = cn(
  "font-fortune-serif font-bold whitespace-nowrap leading-none",
  "text-[clamp(0.78rem,1.25vw,1.35rem)] text-white tracking-[0.02em]",
  // 600px 이하 portrait — 카드 값 축소해서 카드 안에 깔끔히 들어오도록.
  "max-[600px]:portrait:text-[clamp(0.38rem,1.1vw,0.52rem)]",
  "[text-shadow:0_0_0.45rem_rgba(220,170,255,0.75),0_0_0.14rem_rgba(255,255,255,0.55)]",
);
const INFO_CARD_DASH_CLASS = "w-[52%] h-auto pointer-events-none";

const ACTION_ICON_CLASS = cn(
  "w-[clamp(0.85rem,1.4vw,1.3rem)] h-[clamp(0.85rem,1.4vw,1.3rem)]",
  "max-[767px]:portrait:w-[clamp(1rem,3.4vw,1.25rem)] max-[767px]:portrait:h-[clamp(1rem,3.4vw,1.25rem)]",
);

// 하단 버튼 행 — 뷰포트 좌표계에 고정. 뷰포트 하단에 가로 전체 폭으로 깔리고,
// 내부 버튼들은 flex justify-center로 중앙 정렬됨. 스테이지가 아무리 넓어져도
// 화면 밖으로 밀려나지 않음.
const ACTIONS_ROW_CLASS = cn(
  "absolute inset-x-0 z-[7]",
  "bottom-[clamp(2dvh,5dvh,7dvh)]",
  "flex items-center justify-center gap-[clamp(0.8rem,1.6vw,2.2rem)]",
  "px-[clamp(0.5rem,2vw,2rem)]",
  "pointer-events-none [&>*]:pointer-events-auto",
  DRAW_ACTIONS_RISE,
  "[animation-delay:3120ms]",
);

export default function FortuneDrawPanel({
  onDraw,
  onEdit,
}: FortuneDrawPanelProps) {
  const { birthInfo, isDrawing } = useFortuneSessionStore(
    useShallow((state) => ({
      birthInfo: state.birthInfo,
      isDrawing: state.isDrawingFortune,
    })),
  );
  const saju = useMemo(() => {
    if (!isBirthInfoComplete(birthInfo)) return null;
    try {
      return calculateFortuneSaju(birthInfo);
    } catch {
      return null;
    }
  }, [birthInfo]);

  const calendarLabel = birthInfo.calendarType === "solar" ? "양력" : "음력";
  const timeLabel = birthInfo.timeUnknown ? "시간 모름" : birthInfo.birthTime;
  const pillarValueByKey = saju
    ? {
        year: saju.sajuYear,
        month: saju.sajuMonth,
        day: saju.sajuDay,
        hour: saju.sajuHour,
      }
    : null;

  return (
    <div
      className="fixed inset-0 z-5 overflow-hidden pointer-events-none"
      aria-label="사주 입력 정보 확인"
    >
      {/* 스테이지 좌표계 레이어 — 말풍선 + 입력 정보 패널이 포포 기준으로 따라다님 */}
      <div className={STAGE_LAYER_CLASS}>
        {/* 말풍선 — 프레임 이미지 + 텍스트 오버레이 */}
        <div className={SPEECH_BUBBLE_WRAPPER_CLASS}>
          <img
            className={SPEECH_BUBBLE_FRAME_CLASS}
            src="/images/fortune/draw/speech-bubble.png"
            alt=""
            draggable={false}
            onDragStart={(event) => event.preventDefault()}
          />
          <p
            className={SPEECH_BUBBLE_TEXT_CLASS}
            aria-label="포포: 좋아 이 정보 맞지? 그럼 네모닉에 마법을 걸어 오늘의 운세 메모를 뽑아보자."
          >
            좋아 이 정보 맞지?
            <br />
            그럼 <span className={SPEECH_BUBBLE_NEMONIC_CLASS}>네모닉</span>에
            마법을 걸어 오늘의 운세 메모를 뽑아보자.
          </p>
        </div>
        <div className={INFO_PANEL_WRAPPER_CLASS} aria-hidden={false}>
          {/* 꼬리 — 패널 좌하단에서 매직 큐브 방향으로 뻗어나감 */}
          <img
            className={INFO_PANEL_TAIL_CLASS}
            src="/images/fortune/draw/info-panel-tail.png"
            alt=""
            draggable={false}
            onDragStart={(event) => event.preventDefault()}
          />
          {/* 메인 프레임 */}
          <img
            className={INFO_PANEL_FRAME_CLASS}
            src="/images/fortune/draw/info-panel.png"
            alt=""
            draggable={false}
            onDragStart={(event) => event.preventDefault()}
          />
          {/* 타이틀 plaque + 텍스트 */}
          <div className={INFO_TITLE_BOX_CLASS}>
            <img
              className={INFO_TITLE_BG_CLASS}
              src="/images/fortune/draw/info-title.png"
              alt=""
              draggable={false}
              onDragStart={(event) => event.preventDefault()}
            />
            <span className={INFO_TITLE_TEXT_CLASS}>입력 정보 확인</span>
          </div>
          {/* 날짜 텍스트 */}
          <p className={INFO_DATE_CLASS}>
            {calendarLabel} {birthInfo.birthDate} {timeLabel}
          </p>
          {/* 사주 4기둥 카드 */}
          {pillarValueByKey && (
            <ul className={INFO_CARDS_GRID_CLASS}>
              {PILLAR_KEYS.map((key) => (
                <li key={key} className={INFO_CARD_CLASS}>
                  <img
                    className={INFO_CARD_FRAME_CLASS}
                    src="/images/fortune/draw/info-card.png"
                    alt=""
                    draggable={false}
                    onDragStart={(event) => event.preventDefault()}
                  />
                  <div className={INFO_CARD_CONTENT_CLASS}>
                    <span className={INFO_CARD_LABEL_CLASS}>
                      {PILLAR_LABEL_BY_KEY[key]}
                    </span>
                    <img
                      className={INFO_CARD_DASH_CLASS}
                      src="/images/fortune/draw/info-card-dash.png"
                      alt=""
                      draggable={false}
                      onDragStart={(event) => event.preventDefault()}
                    />
                    <span className={INFO_CARD_VALUE_CLASS}>
                      {pillarValueByKey[key]}
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      {/* 뷰포트 고정 액션 행 — 항상 뷰포트 하단 중앙에 고정 */}
      <div className={ACTIONS_ROW_CLASS}>
        <FortuneDrawAction
          tone="edit"
          icon={<Feather className={ACTION_ICON_CLASS} aria-hidden />}
          onClick={onEdit}
        >
          수정하기
        </FortuneDrawAction>
        <FortuneDrawAction
          tone="print"
          icon={<Sparkles className={ACTION_ICON_CLASS} aria-hidden />}
          disabled={isDrawing}
          onClick={onDraw}
        >
          {isDrawing ? "준비 중" : "운세 인쇄"}
        </FortuneDrawAction>
      </div>
    </div>
  );
}
