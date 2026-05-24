import { Moon, Sun } from "lucide-react";
import { useState, type ChangeEvent, type FormEvent } from "react";
import { useShallow } from "zustand/react/shallow";

import { cn } from "@/shared/libs";

import { useFortuneSessionStore, type FortuneCalendarType } from '..';
import { isBirthInfoComplete } from '../utils';import FortuneBirthOptionButton from "./FortuneBirthOptionButton";

interface FortuneBirthFormProps {
  onSubmit: () => Promise<void>;
}

type BirthDatePart = "year" | "month" | "day";
type BirthTimePart = "hour" | "minute";

interface BirthDateParts {
  year: string;
  month: string;
  day: string;
}

interface BirthTimeParts {
  hour: string;
  minute: string;
}

const FIRST_BIRTH_YEAR = 1900;
const CURRENT_YEAR = new Date().getFullYear();
const BIRTH_YEAR_OPTIONS = Array.from(
  { length: CURRENT_YEAR - FIRST_BIRTH_YEAR + 1 },
  (_, yearIndex) => String(CURRENT_YEAR - yearIndex),
);
const BIRTH_MONTH_OPTIONS = Array.from({ length: 12 }, (_, monthIndex) =>
  padDatePart(monthIndex + 1),
);
const BIRTH_HOUR_OPTIONS = Array.from({ length: 24 }, (_, hourIndex) =>
  padDatePart(hourIndex),
);
const BIRTH_MINUTE_OPTIONS = Array.from({ length: 12 }, (_, minuteIndex) =>
  padDatePart(minuteIndex * 5),
);

const STAGGER_RISE =
  "animate-fortune-birth-element-rise motion-reduce:animate-none relative z-1 min-w-0 max-w-full";

const FIELDSET_CLASS = cn(
  STAGGER_RISE,
  "grid gap-[0.2rem] m-0 p-0 border-0 w-full max-w-full min-w-0 box-border",
  "max-[800px]:gap-[0.18rem]",
);

const LEGEND_CLASS = cn(
  "float-left w-full mb-[0.18rem]",
  "font-fortune-eulyoo text-[0.88rem] font-normal tracking-[0.02em] leading-tight",
  "text-[rgba(255,244,207,0.96)]",
  "[text-shadow:0_0.06rem_0.18rem_rgba(7,1,20,0.78),0_0_0.36rem_rgba(187,101,255,0.28)]",
  // spark icons rendered via ::before / ::after
  "before:content-[''] after:content-['']",
  "before:inline-block after:inline-block",
  "before:w-[1.05em] before:h-[1.25em] after:w-[1.05em] after:h-[1.25em]",
  "before:mx-[0.32rem] after:mx-[0.32rem]",
  "before:bg-[url('/images/fortune/form/birth-spark.png')] before:bg-center before:bg-no-repeat before:bg-contain",
  "after:bg-[url('/images/fortune/form/birth-spark.png')] after:bg-center after:bg-no-repeat after:bg-contain",
  "before:[filter:drop-shadow(0_0_0.32rem_rgba(223,136,255,0.42))] after:[filter:drop-shadow(0_0_0.32rem_rgba(223,136,255,0.42))]",
  "before:translate-y-[0.16em] after:translate-y-[0.16em]",
  "before:align-middle after:align-middle",
  // mobile overrides
  "max-[800px]:mb-[0.12rem] max-[800px]:text-[0.82rem]",
);

const SELECT_GRID_CLASS = cn(
  "grid w-full min-w-0 max-w-full gap-[0.3rem]",
  "max-[800px]:gap-[0.24rem]",
);

const UNKNOWN_TEXT_CLASS = cn(
  "inline-flex w-max h-auto min-w-max shrink-0 grow-0 items-center",
  "bg-transparent text-[rgba(255,244,218,0.94)]",
  "leading-none whitespace-nowrap",
);

const BIRTH_FORM_FRAME_CLASS = cn(
  // outermost layer — frame image + sizing + frame-edge padding only.
  // The padding sets aside space for the frame image's decorative gold border so
  // its inner content area (= the CLIP layer) lands inside the safe zone.
  "relative mx-auto w-full border-0 grid grid-rows-[minmax(0,1fr)]",
  "font-[var(--font-paperlogy)] text-[rgba(255,245,216,0.98)]",
  "max-h-[calc(100dvh-4.4rem)]",
  "bg-[url('/images/fortune/form/birth-panel-frame.png')] bg-center bg-no-repeat bg-[length:100%_100%]",
  "[filter:drop-shadow(0_1.2rem_2.2rem_rgba(5,1,19,0.52))_drop-shadow(0_0_1.45rem_rgba(179,92,255,0.26))]",
  // padding aligned with frame image's decorative gold edges
  "pt-[1.4rem] pr-[1.4rem] pb-[2rem] pl-[1.4rem]",
  // decorative orbs via ::before / ::after — sit on the frame layer
  "before:content-[''] before:absolute before:z-0 before:pointer-events-none before:opacity-[0.12]",
  "before:w-[5.25rem] before:aspect-square",
  "before:bg-[url('/images/fortune/form/birth-orb.png')] before:bg-center before:bg-no-repeat before:bg-contain",
  "before:top-[13.2%] before:left-[4.8%]",
  "after:content-[''] after:absolute after:z-0 after:pointer-events-none after:opacity-[0.12]",
  "after:w-[5.25rem] after:aspect-square",
  "after:bg-[url('/images/fortune/form/birth-orb.png')] after:bg-center after:bg-no-repeat after:bg-contain",
  "after:right-[5.2%] after:bottom-[14%] after:rotate-[28deg]",
  // mobile overrides
  "max-[800px]:max-h-[calc(100dvh-4.4rem)]",
  "max-[800px]:p-[1.4rem_1.2rem_2rem]",
  "max-[800px]:before:w-[4.5rem] max-[800px]:before:opacity-[0.1]",
  "max-[800px]:after:w-[4.5rem] max-[800px]:after:opacity-[0.1]",
);

const BIRTH_FORM_CLIP_CLASS = cn(
  // middle layer — defines the frame's inner safe area and clips anything that
  // tries to escape (decorative edges, overflow beyond max-h, etc.).
  "relative z-1 w-full min-h-0 max-h-full overflow-hidden",
);

const BIRTH_FORM_CONTENT_CLASS = cn(
  // innermost layer — content grid + vertical scrolling within the clip area.
  "grid content-start w-full max-h-full overflow-y-auto overflow-x-hidden",
  "[scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
  "gap-[0.28rem]",
  // mobile overrides
  "max-[800px]:gap-[0.24rem]",
);

const SEGMENTED_CLASS = cn(
  "relative w-full min-w-0 max-w-full items-stretch overflow-hidden box-border isolate",
  "grid grid-cols-2",
  "min-h-[2.2rem]",
  "p-[0.18rem]",
  "bg-[url('/images/fortune/form/birth-segment-base.png')] bg-center bg-[length:100%_100%] bg-no-repeat",
  "rounded-full",
  // sliding indicator via ::before
  "before:content-[''] before:absolute before:top-1/2 before:z-0 before:pointer-events-none",
  "before:left-[clamp(0.14rem,0.55vw,0.42rem)]",
  "before:w-[calc(50%-clamp(0.12rem,0.42vw,0.3rem))] before:h-[92%]",
  "before:[transform:translate3d(0,-50%,0)]",
  "before:bg-[url('/images/fortune/form/birth-segment-indicator.png')] before:bg-center before:bg-[length:100%_100%] before:bg-no-repeat",
  "before:[transition:transform_320ms_cubic-bezier(0.2,0.92,0.22,1)]",
  // lunar state slides indicator right
  "data-[calendar=lunar]:before:[transform:translate3d(calc(100%+clamp(0.12rem,0.42vw,0.3rem)),-50%,0)]",
  // mobile overrides
  "max-[800px]:min-h-[2rem] max-[800px]:p-[0.16rem]",
);

const SELECT_FIELD_BASE_CLASS = cn(
  "relative grid min-w-0 max-w-full box-border",
  "min-h-[2.1rem]",
  // 프레임이 셀을 벗어나지 않도록 padding으로 시각 여백 확보 후 ::before는 inset:0
  "p-[0.22rem_0.36rem] max-[800px]:p-[0.2rem_0.3rem]",
  // purple frame decoration via ::before (셀 박스 안에 fit)
  "before:content-[''] before:absolute before:inset-0 before:pointer-events-none",
  "before:bg-[url('/images/fortune/form/birth-select-frame.png')] before:bg-center before:bg-[length:100%_100%] before:bg-no-repeat",
  "before:[filter:drop-shadow(0_0.22rem_0.36rem_rgba(8,1,18,0.28))]",
  // yellow chevron decoration via ::after
  "after:content-[''] after:absolute after:top-1/2 after:right-[clamp(0.62rem,1.4vw,1rem)] after:z-[2] after:pointer-events-none",
  "after:w-[clamp(0.55rem,0.95vw,0.78rem)] after:h-[clamp(0.55rem,0.95vw,0.78rem)]",
  "after:[transform:translateY(-68%)_rotate(45deg)]",
  "after:border-r-[0.18rem] after:border-b-[0.18rem] after:border-[rgba(255,216,117,0.96)]",
  "after:[filter:drop-shadow(0_0_0.22rem_rgba(255,216,117,0.55))_drop-shadow(0_0_0.6rem_rgba(255,176,68,0.32))]",
  // mobile overrides
  "max-[800px]:min-h-[2rem]",
);

const SELECT_FIELD_LABEL_CLASS = cn(
  "absolute top-1/2 right-[clamp(1.4rem,2.8vw,2.1rem)] -translate-y-1/2 z-[2] pointer-events-none opacity-0",
  "[transition:opacity_160ms_ease]",
  "font-fortune-serif text-[0.76rem] [font-weight:850]",
  "text-[rgba(255,235,177,0.78)]",
  "[text-shadow:0_0.1rem_0.24rem_rgba(5,1,16,0.72)]",
  // when sibling select is disabled
  "peer-disabled:opacity-[0.35]",
  // mobile overrides
  "max-[800px]:right-[1.82rem] max-[800px]:text-[0.72rem]",
);

const SELECT_CLASS = cn(
  "peer relative z-1 w-full cursor-pointer border-0 outline-none bg-transparent bg-none",
  "appearance-none [-webkit-appearance:none] [-moz-appearance:none]",
  "min-h-[2.2rem] max-[800px]:min-h-[2.2rem]",
  "font-fortune-serif text-[0.88rem] [font-weight:760]",
  "text-[rgba(255,246,224,0.98)]",
  "pt-0 pb-0 pr-[1.8rem] pl-[1.2rem]",
  "text-center [text-align-last:center]",
  "[text-shadow:0_0.1rem_0.26rem_rgba(7,1,18,0.78),0_0_0.36rem_rgba(175,92,255,0.22)]",
  "shadow-none",
  // option element coloring
  "[&_option]:text-[#241137] [&_option]:bg-[#fbf4ff]",
  // suppress browser focus ring
  "focus:outline-none focus-visible:outline-none focus:shadow-none focus-visible:shadow-none",
  "disabled:cursor-not-allowed disabled:text-[rgba(255,239,213,0.42)]",
  // mobile overrides
  "max-[800px]:pr-[2.34rem] max-[800px]:pl-[0.72rem] max-[800px]:text-[0.86rem]",
);

const CHECKBOX_CLASS = cn(
  "relative grid place-items-center flex-none border-0 rounded-none shadow-none",
  "w-[1.4rem] h-auto aspect-square",
  "bg-[url('/images/fortune/form/birth-checkbox-frame.png')] bg-center bg-no-repeat bg-contain",
  "[filter:drop-shadow(0_0_0.32rem_rgba(196,100,255,0.28))]",
  // checkmark via ::after, hidden by default, shown when peer (input) is checked
  "after:content-[''] after:opacity-0",
  "after:w-[0.48rem] after:h-[0.88rem]",
  "after:[transform:translateY(-0.08rem)_rotate(42deg)]",
  "after:[border-right:0.18rem_solid_rgba(255,244,215,0.96)] after:[border-bottom:0.18rem_solid_rgba(255,244,215,0.96)]",
  "after:[filter:drop-shadow(0_0_0.32rem_rgba(255,126,246,0.72))]",
  "peer-checked:after:opacity-100",
  // mobile overrides
  "max-[800px]:w-[1.28rem] max-[800px]:h-auto",
);

const SUBMIT_CLASS = cn(
  "group/submit relative flex items-center justify-center border-0 overflow-hidden",
  "w-[min(100%,18rem)] min-h-auto aspect-[1876/358] justify-self-center",
  "bg-transparent bg-[url('/images/fortune/form/birth-submit-bar.png')] bg-center bg-[length:100%_100%] bg-no-repeat",
  "text-[rgba(255,252,232,1)] font-fortune-eulyoo [font-weight:600]",
  "text-[0.88rem] tracking-[0.04em] leading-none",
  "pt-0 pb-[0.04rem] pr-[2.8rem] pl-[1.4rem]",
  "[text-shadow:0_0.04rem_0.16rem_rgba(9,1,20,0.85)]",
  "[text-wrap:nowrap] whitespace-nowrap break-keep",
  // enabled state (no glow — keeps the button flush inside the clip area)
  "enabled:[filter:saturate(1.18)_brightness(1.12)]",
  // disabled state
  "disabled:opacity-[0.55] disabled:[filter:saturate(0.6)_brightness(0.85)]",
  // hover enabled
  "enabled:hover:[filter:saturate(1.3)_brightness(1.18)]",
  // mobile overrides
  "max-[800px]:w-[min(100%,16rem)] max-[800px]:min-h-auto",
  "max-[800px]:pr-[2.4rem] max-[800px]:pl-[1rem]",
  "max-[800px]:text-[0.82rem]",
);

const SUBMIT_COPY_CLASS = cn(
  "relative block z-1 max-w-full overflow-hidden leading-none",
  "[text-overflow:clip] [text-wrap:nowrap] whitespace-nowrap break-keep",
);

const SUBMIT_ORB_CLASS = cn(
  "absolute top-1/2 right-[clamp(0.34rem,0.9vw,0.56rem)]",
  "w-[2.6rem] aspect-square",
  "[transform:translateY(-50%)]",
  "bg-[url('/images/fortune/form/birth-submit-orb.png')] bg-center bg-no-repeat bg-contain",
  "pointer-events-none",
  "[transition:transform_180ms_ease]",
  // hover on parent submit (enabled)
  "group-enabled/submit:group-hover/submit:[transform:translate(0.12rem,-50%)_scale(1.035)]",
  // mobile overrides
  "max-[800px]:right-[0.2rem] max-[800px]:w-[2.2rem]",
);

export default function FortuneBirthForm({ onSubmit }: FortuneBirthFormProps) {
  const { birthInfo, isSubmitting, setBirthInfo } = useFortuneSessionStore(
    useShallow((state) => ({
      birthInfo: state.birthInfo,
      isSubmitting: state.isSubmittingBirthInfo,
      setBirthInfo: state.setBirthInfo,
    })),
  );
  const isComplete = isBirthInfoComplete(birthInfo);
  const [birthDateParts, setBirthDateParts] = useState<BirthDateParts>(() =>
    splitBirthDate(birthInfo.birthDate),
  );
  const [birthTimeParts, setBirthTimeParts] = useState<BirthTimeParts>(() =>
    splitBirthTime(birthInfo.birthTime),
  );
  const birthDayOptions = createBirthDayOptions(
    birthDateParts.year,
    birthDateParts.month,
  );

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void onSubmit();
  };

  const updateCalendarType = (calendarType: FortuneCalendarType) => {
    setBirthInfo({ ...birthInfo, calendarType });
  };

  const updateBirthDatePart = (part: BirthDatePart, value: string) => {
    const nextBirthDateParts = normalizeBirthDateParts({
      ...birthDateParts,
      [part]: value,
    });

    setBirthDateParts(nextBirthDateParts);
    setBirthInfo({
      ...birthInfo,
      birthDate: formatBirthDate(nextBirthDateParts),
    });
  };

  const updateBirthTimePart = (part: BirthTimePart, value: string) => {
    const nextBirthTimeParts = {
      ...birthTimeParts,
      [part]: value,
    };

    setBirthTimeParts(nextBirthTimeParts);
    setBirthInfo({
      ...birthInfo,
      birthTime: formatBirthTime(nextBirthTimeParts),
      timeUnknown: false,
    });
  };

  const updateTimeUnknown = (event: ChangeEvent<HTMLInputElement>) => {
    const isTimeUnknown = event.target.checked;

    if (isTimeUnknown) {
      setBirthTimeParts({ hour: "", minute: "" });
    }

    setBirthInfo({
      ...birthInfo,
      birthTime: isTimeUnknown ? "" : birthInfo.birthTime,
      timeUnknown: isTimeUnknown,
    });
  };

  return (
    <form className={BIRTH_FORM_FRAME_CLASS} onSubmit={handleSubmit}>
      <div className={BIRTH_FORM_CLIP_CLASS}>
        <div className={BIRTH_FORM_CONTENT_CLASS}>
          <div
            className={cn(
              STAGGER_RISE,
              "grid justify-items-center text-center [animation-delay:80ms] gap-[0.2rem] mt-[0.1rem]",
            )}
          >
            <span
              aria-hidden
              className={cn(
                "block w-[2.2rem] aspect-596/677 mb-[-0.2rem] opacity-90",
                "bg-[url('/images/fortune/form/birth-spark.png')] bg-center bg-no-repeat bg-contain",
                "[filter:drop-shadow(0_0_0.7rem_rgba(215,96,255,0.48))]",
                "max-[800px]:w-[1.8rem] max-[800px]:mb-[-0.14rem]",
              )}
            />
            <h1
              className={cn(
                "m-0 w-full font-fortune-serif font-bold leading-snug text-balance",
                "text-[0.95rem]",
                "text-[rgba(255,249,226,0.98)] tracking-[-0.01em] break-keep",
                "[text-shadow:0_0.12rem_0.42rem_rgba(11,1,25,0.82),0_0_0.82rem_rgba(207,130,255,0.45)]",
                "max-[800px]:text-[0.86rem]",
              )}
            >
              오늘의 운세를 위한 사주 정보를 알려줘
            </h1>
            <p
              className={cn(
                "m-0 w-full max-w-[30rem] font-fortune-eulyoo font-normal leading-[1.4] break-keep",
                "text-[0.78rem]",
                "text-[rgba(255,247,225,0.82)]",
                "[text-shadow:0_0.1rem_0.3rem_rgba(7,1,18,0.7)]",
                "max-[800px]:text-[0.7rem] max-[800px]:leading-[1.3]",
              )}
            >
              입력한 정보로 오늘의 운세 메모를 정성껏 준비할게요.
            </p>
          </div>

          <fieldset className={cn(FIELDSET_CLASS, "[animation-delay:180ms]")}>
            <legend className={LEGEND_CLASS}>날짜 기준</legend>
            <div
              className={SEGMENTED_CLASS}
              data-calendar={birthInfo.calendarType}
              role="group"
              aria-label="양력 음력 선택"
            >
              <FortuneBirthOptionButton
                state={birthInfo.calendarType === "solar" ? "active" : "idle"}
                icon={
                  <Sun
                    className="w-[1.32em] h-[1.32em] text-[rgba(255,216,117,0.96)] [stroke-width:2.15]"
                    aria-hidden
                  />
                }
                aria-pressed={birthInfo.calendarType === "solar"}
                onClick={() => updateCalendarType("solar")}
              >
                양력
              </FortuneBirthOptionButton>
              <FortuneBirthOptionButton
                state={birthInfo.calendarType === "lunar" ? "active" : "idle"}
                icon={
                  <Moon
                    className="w-[1.32em] h-[1.32em] text-[rgba(255,216,117,0.96)] [stroke-width:2.15]"
                    aria-hidden
                  />
                }
                aria-pressed={birthInfo.calendarType === "lunar"}
                onClick={() => updateCalendarType("lunar")}
              >
                음력
              </FortuneBirthOptionButton>
            </div>
          </fieldset>

          <fieldset className={cn(FIELDSET_CLASS, "[animation-delay:260ms]")}>
            <legend className={LEGEND_CLASS}>생년월일</legend>
            <div
              className={cn(
                SELECT_GRID_CLASS,
                "grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,1fr)]",
              )}
            >
              <label
                className={selectFieldClassName(Boolean(birthDateParts.year))}
              >
                <select
                  required
                  className={SELECT_CLASS}
                  value={birthDateParts.year}
                  onChange={(event) =>
                    updateBirthDatePart("year", event.target.value)
                  }
                >
                  <option value=""></option>
                  {BIRTH_YEAR_OPTIONS.map((yearOption) => (
                    <option key={yearOption} value={yearOption}>
                      {yearOption}
                    </option>
                  ))}
                </select>
                <span
                  className={cn(
                    SELECT_FIELD_LABEL_CLASS,
                    Boolean(birthDateParts.year) && "opacity-100",
                  )}
                >
                  년
                </span>
              </label>
              <label
                className={selectFieldClassName(Boolean(birthDateParts.month))}
              >
                <select
                  required
                  className={SELECT_CLASS}
                  value={birthDateParts.month}
                  onChange={(event) =>
                    updateBirthDatePart("month", event.target.value)
                  }
                >
                  <option value=""></option>
                  {BIRTH_MONTH_OPTIONS.map((monthOption) => (
                    <option key={monthOption} value={monthOption}>
                      {Number(monthOption)}
                    </option>
                  ))}
                </select>
                <span
                  className={cn(
                    SELECT_FIELD_LABEL_CLASS,
                    Boolean(birthDateParts.month) && "opacity-100",
                  )}
                >
                  월
                </span>
              </label>
              <label
                className={selectFieldClassName(Boolean(birthDateParts.day))}
              >
                <select
                  required
                  className={SELECT_CLASS}
                  value={birthDateParts.day}
                  onChange={(event) =>
                    updateBirthDatePart("day", event.target.value)
                  }
                >
                  <option value=""></option>
                  {birthDayOptions.map((dayOption) => (
                    <option key={dayOption} value={dayOption}>
                      {Number(dayOption)}
                    </option>
                  ))}
                </select>
                <span
                  className={cn(
                    SELECT_FIELD_LABEL_CLASS,
                    Boolean(birthDateParts.day) && "opacity-100",
                  )}
                >
                  일
                </span>
              </label>
            </div>
          </fieldset>

          <fieldset className={cn(FIELDSET_CLASS, "[animation-delay:340ms]")}>
            <legend className={LEGEND_CLASS}>태어난 시</legend>
            <div className={cn(SELECT_GRID_CLASS, "grid-cols-2")}>
              <label
                className={selectFieldClassName(Boolean(birthTimeParts.hour))}
              >
                <select
                  required={!birthInfo.timeUnknown}
                  disabled={birthInfo.timeUnknown}
                  className={SELECT_CLASS}
                  value={birthTimeParts.hour}
                  onChange={(event) =>
                    updateBirthTimePart("hour", event.target.value)
                  }
                >
                  <option value=""></option>
                  {BIRTH_HOUR_OPTIONS.map((hourOption) => (
                    <option key={hourOption} value={hourOption}>
                      {Number(hourOption)}
                    </option>
                  ))}
                </select>
                <span
                  className={cn(
                    SELECT_FIELD_LABEL_CLASS,
                    Boolean(birthTimeParts.hour) && "opacity-100",
                  )}
                >
                  시
                </span>
              </label>
              <label
                className={selectFieldClassName(Boolean(birthTimeParts.minute))}
              >
                <select
                  required={!birthInfo.timeUnknown}
                  disabled={birthInfo.timeUnknown}
                  className={SELECT_CLASS}
                  value={birthTimeParts.minute}
                  onChange={(event) =>
                    updateBirthTimePart("minute", event.target.value)
                  }
                >
                  <option value=""></option>
                  {BIRTH_MINUTE_OPTIONS.map((minuteOption) => (
                    <option key={minuteOption} value={minuteOption}>
                      {minuteOption}
                    </option>
                  ))}
                </select>
                <span
                  className={cn(
                    SELECT_FIELD_LABEL_CLASS,
                    Boolean(birthTimeParts.minute) && "opacity-100",
                  )}
                >
                  분
                </span>
              </label>
            </div>

            <label
              data-fortune-tap-target
              className={cn(
                "inline-flex w-fit items-center cursor-pointer break-keep",
                "gap-[0.4rem] min-h-[1.8rem] mt-[0.04rem]",
                "font-fortune-eulyoo text-[0.82rem] font-normal",
                "text-[rgba(255,244,218,0.94)]",
                "[text-shadow:0_0.1rem_0.24rem_rgba(8,1,19,0.72)]",
                "max-[800px]:text-[0.9rem]",
              )}
            >
              <input
                type="checkbox"
                checked={birthInfo.timeUnknown}
                onChange={updateTimeUnknown}
                className="peer absolute opacity-0 pointer-events-none"
              />
              <span className={CHECKBOX_CLASS} aria-hidden />
              <span className={UNKNOWN_TEXT_CLASS}>시간 모름</span>
            </label>
            <p
              className={cn(
                "mt-[-0.1rem] ml-[1.8rem]",
                "font-fortune-eulyoo text-[0.72rem] font-normal leading-[1.35] break-keep",
                "text-[rgba(255,235,206,0.72)]",
                "[text-shadow:0_0.1rem_0.24rem_rgba(8,1,18,0.72)]",
                "max-[800px]:mt-[-0.1rem] max-[800px]:ml-[1.52rem] max-[800px]:text-[0.64rem]",
              )}
            >
              태어난 시간을 모르면 체크해도 괜찮아요.
            </p>
          </fieldset>

          <div
            className={cn(
              STAGGER_RISE,
              "grid justify-items-center mt-[0.16rem] [animation-delay:420ms]",
              "max-[800px]:mt-[0.02rem]",
            )}
          >
            <button
              type="submit"
              disabled={!isComplete || isSubmitting}
              className={SUBMIT_CLASS}
            >
              <span className={SUBMIT_COPY_CLASS}>
                {isSubmitting ? "정보 저장 중" : "오늘의 운세 인쇄하기"}
              </span>
              <span className={SUBMIT_ORB_CLASS} aria-hidden />
            </button>
          </div>
        </div>
      </div>
    </form>
  );
}

function selectFieldClassName(_hasValue: boolean) {
  // hasValue visibility is driven by the inline opacity utility on the label span.
  return SELECT_FIELD_BASE_CLASS;
}

function splitBirthDate(birthDate: string): BirthDateParts {
  const [year = "", month = "", day = ""] = birthDate.split("-");

  return { year, month, day };
}

function splitBirthTime(birthTime: string): BirthTimeParts {
  const [hour = "", minute = ""] = birthTime.split(":");

  return { hour, minute };
}

function normalizeBirthDateParts(
  birthDateParts: BirthDateParts,
): BirthDateParts {
  if (!birthDateParts.year || !birthDateParts.month || !birthDateParts.day) {
    return birthDateParts;
  }

  const maxDay = getDaysInMonth(
    Number(birthDateParts.year),
    Number(birthDateParts.month),
  );

  if (Number(birthDateParts.day) <= maxDay) {
    return birthDateParts;
  }

  return {
    ...birthDateParts,
    day: "",
  };
}

function formatBirthDate(birthDateParts: BirthDateParts) {
  if (!birthDateParts.year || !birthDateParts.month || !birthDateParts.day) {
    return "";
  }

  return `${birthDateParts.year}-${birthDateParts.month}-${birthDateParts.day}`;
}

function formatBirthTime(birthTimeParts: BirthTimeParts) {
  if (!birthTimeParts.hour || !birthTimeParts.minute) {
    return "";
  }

  return `${birthTimeParts.hour}:${birthTimeParts.minute}`;
}

function createBirthDayOptions(year: string, month: string) {
  const dayCount =
    year && month ? getDaysInMonth(Number(year), Number(month)) : 31;

  return Array.from({ length: dayCount }, (_, dayIndex) =>
    padDatePart(dayIndex + 1),
  );
}

function getDaysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate();
}

function padDatePart(value: number) {
  return String(value).padStart(2, "0");
}
