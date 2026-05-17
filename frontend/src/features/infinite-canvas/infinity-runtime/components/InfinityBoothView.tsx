import { useState } from "react";
import Image from "next/image";
import { ArrowRight, DoorOpen, Sparkles } from "lucide-react";
import { Button } from "@/shared/components";
import { cn } from "@/shared/libs";
import { INFINITY_COLORS } from "../constants";

interface InfinityBoothViewProps {
  inviteCodeError: string | null;
  isBusy: boolean;
  isUserReady: boolean;
  selectedColor: string;
  onClearError: () => void;
  onCreateCanvas: () => void;
  onJoinCanvas: (inviteCode: string) => void;
  onSelectColor: (color: string) => void;
}

const INFINITY_BRUSH_MOODS = [
  { label: "Brush", color: "#ff82c0" },
  { label: "Shape", color: "#68e4d9" },
  { label: "Text", color: "#ffd84d" },
] as const;

const INFINITY_MAGIC_STARS = [
  "left-[8%] top-[15%] size-3 sm:size-4",
  "left-[17%] bottom-[22%] size-4 sm:size-5",
  "left-[37%] top-[24%] size-3 sm:size-4",
  "left-[54%] bottom-[16%] size-3 sm:size-4",
  "right-[24%] top-[13%] size-4 sm:size-5",
  "right-[10%] top-[38%] size-3 sm:size-4",
  "right-[15%] bottom-[20%] size-4 sm:size-5",
] as const;

export function InfinityBoothView({
  inviteCodeError,
  isBusy,
  isUserReady,
  selectedColor,
  onClearError,
  onCreateCanvas,
  onJoinCanvas,
  onSelectColor,
}: InfinityBoothViewProps) {
  const [inviteCodeDraft, setInviteCodeDraft] = useState("");
  const [isJoinFormOpen, setIsJoinFormOpen] = useState(false);
  const isActionDisabled = !isUserReady || isBusy;

  const handleSubmitInvite = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onJoinCanvas(inviteCodeDraft);
  };

  const handlePointerMove = (event: React.PointerEvent<HTMLElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect();
    const pointerX = (event.clientX - bounds.left) / bounds.width - 0.5;
    const pointerY = (event.clientY - bounds.top) / bounds.height - 0.5;
    event.currentTarget.style.setProperty("--infinity-pointer-x", pointerX.toFixed(3));
    event.currentTarget.style.setProperty("--infinity-pointer-y", pointerY.toFixed(3));
  };

  const handlePointerLeave = (event: React.PointerEvent<HTMLElement>) => {
    event.currentTarget.style.setProperty("--infinity-pointer-x", "0");
    event.currentTarget.style.setProperty("--infinity-pointer-y", "0");
  };

  return (
    <section
      className="infinity-booth-shell relative min-h-dvh overflow-hidden bg-[#075c92]"
      onPointerMove={handlePointerMove}
      onPointerLeave={handlePointerLeave}
    >
      <div
        aria-hidden
        className="infinity-background-art absolute inset-0 bg-[url('/images/infinite-canvas/sky-booth-background.png')] bg-cover bg-center"
      />
      <InfinityMagicField />

      <div className="relative z-10 mx-auto grid min-h-dvh w-full max-w-[1500px] items-center gap-6 px-5 pb-10 pt-24 sm:px-[6%] sm:pt-28 lg:grid-cols-[minmax(340px,500px)_minmax(520px,1fr)] lg:gap-9 lg:pb-16 lg:pt-24 xl:grid-cols-[minmax(390px,540px)_minmax(560px,1fr)]">
        <div className="flex w-full flex-col items-center text-center lg:items-start lg:text-left">
          <span className="infinity-badge body-b inline-flex min-h-10 items-center gap-2 rounded-full px-4 sm:min-h-11 sm:px-5">
            <Sparkles className="size-4 text-[#ff82c0]" aria-hidden />
            같이 번지는 무한한 도화지
          </span>

          <h1 className="mt-7 contents">
            <Image
              src="/images/infinite-canvas/title.svg"
              alt="무한 캔버스"
              width={680}
              height={300}
              priority
              className="infinity-logo h-auto w-full max-w-[min(92vw,470px)] sm:max-w-[500px] lg:max-w-[520px]"
            />
          </h1>

          <div className="infinity-copy-panel mt-5 w-full max-w-[520px] rounded-[24px] px-4 py-4 text-white sm:mt-7 sm:rounded-[28px] sm:px-5">
            <p className="body-l-r text-[18px] leading-[1.65] drop-shadow-[0_3px_8px_rgba(0,0,0,0.28)] sm:text-[22px]">
              하늘 위 투명 보드에 친구들의 선과 색이 동시에 피어나는 공간.
            </p>
            <div className="mt-4 flex flex-wrap justify-center gap-2 lg:justify-start">
              {INFINITY_BRUSH_MOODS.map((mood) => (
                <span
                  key={mood.label}
                  className="caption-b rounded-full border border-white/54 bg-white/18 px-3 py-1 text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.34)]"
                >
                  <span
                    aria-hidden
                    className="mr-1.5 inline-block size-2.5 rounded-full align-middle"
                    style={{ backgroundColor: mood.color }}
                  />
                  {mood.label}
                </span>
              ))}
            </div>
          </div>

          <div className="infinity-action-panel mt-6 flex w-full max-w-[540px] flex-col gap-4 rounded-[26px] p-4 sm:mt-8 sm:rounded-[30px] sm:p-5">
            <div className="flex flex-wrap gap-3">
              <Button
                type="button"
                size="lg"
                color="blue"
                disabled={isActionDisabled}
                onClick={onCreateCanvas}
                className="infinity-primary-button min-h-14 flex-1 rounded-2xl px-6 transition-transform hover:-translate-y-0.5 hover:brightness-105 sm:flex-none sm:px-8"
              >
                {isBusy ? "만드는 중" : "방 만들기"}
                <ArrowRight className="size-5" aria-hidden />
              </Button>
              <Button
                type="button"
                size="lg"
                color="neutral"
                disabled={isActionDisabled}
                onClick={() => setIsJoinFormOpen((current) => !current)}
                className="infinity-secondary-button min-h-14 flex-1 rounded-2xl px-6 transition-transform hover:-translate-y-0.5 hover:bg-white sm:flex-none sm:px-8"
              >
                <DoorOpen className="size-5" aria-hidden />
                방 입장
              </Button>
            </div>

            {isJoinFormOpen && (
              <form
                className="grid w-full gap-3 rounded-[24px] border border-white/80 bg-white/84 p-4 shadow-[0_12px_28px_rgba(6,30,78,0.18)] backdrop-blur-md sm:grid-cols-[minmax(0,1fr)_126px]"
                onSubmit={handleSubmitInvite}
              >
                <input
                  value={inviteCodeDraft}
                  onChange={(event) => {
                    setInviteCodeDraft(event.target.value);
                    onClearError();
                  }}
                  placeholder="초대코드"
                  aria-label="무한 캔버스 초대코드"
                  className="body-b min-h-14 rounded-full border border-[#9bc5eb] bg-white px-5 text-[#2f3c56] outline-none placeholder:text-[#7890ad] focus:border-[#5b9fdf]"
                />
                <Button
                  type="submit"
                  size="lg"
                  color="neutral"
                  disabled={isActionDisabled}
                  className="min-h-14 whitespace-nowrap border-[#9bc5eb] bg-white px-6 text-[#2f3c56] hover:bg-[#eff7ff]"
                >
                  {isBusy ? "입장 중" : "합류하기"}
                </Button>
                {inviteCodeError && (
                  <p role="alert" className="caption-b rounded-[12px] bg-white/78 px-4 py-3 text-red-500 sm:col-span-2">
                    {inviteCodeError}
                  </p>
                )}
              </form>
            )}

            <div className="flex flex-col gap-3 rounded-[24px] border border-white/68 bg-white/72 p-4 shadow-[0_10px_24px_rgba(6,30,78,0.18)] backdrop-blur-md">
              <p className="body-b text-[#38506f]">내 색 고르기</p>
              <div className="flex flex-wrap gap-3">
                {INFINITY_COLORS.map((swatch) => (
                  <button
                    key={swatch}
                    type="button"
                    aria-label={`${swatch} 색상 선택`}
                    onClick={() => onSelectColor(swatch)}
                    className={cn(
                      "size-11 rounded-full border-2 border-white/90 shadow-[0_5px_12px_rgb(67_102_148_/_14%)] transition-transform hover:scale-105",
                      selectedColor === swatch &&
                        "scale-105 ring-2 ring-[#5b9fdf] ring-offset-2 ring-offset-white",
                    )}
                    style={{ backgroundColor: swatch }}
                  />
                ))}
              </div>
            </div>
          </div>
        </div>

        <InfinityBoothShowcase />
      </div>
    </section>
  );
}

function InfinityBoothShowcase() {
  return (
    <div className="infinity-showcase relative flex w-full items-center justify-center overflow-visible" aria-hidden>
      <div className="infinity-board-aura" />
      <div className="infinity-parallax-layer infinity-panels-layer">
        <Image
          src="/images/infinite-canvas/glass-panels.png"
          alt=""
          width={1394}
          height={785}
          priority
          sizes="(min-width: 1280px) 430px, (min-width: 768px) 38vw, 60vw"
          className="infinity-panels-asset"
        />
      </div>
      <div className="infinity-parallax-layer infinity-ribbon-layer">
        <Image
          src="/images/infinite-canvas/brush-ribbons.png"
          alt=""
          width={1493}
          height={922}
          priority
          sizes="(min-width: 1280px) 720px, (min-width: 768px) 54vw, 82vw"
          className="infinity-ribbon-asset"
        />
      </div>
      <div className="infinity-parallax-layer infinity-tray-layer">
        <Image
          src="/images/infinite-canvas/color-tray.png"
          alt=""
          width={1729}
          height={409}
          sizes="(min-width: 1280px) 260px, (min-width: 768px) 22vw, 40vw"
          className="infinity-tray-asset"
        />
      </div>
      <div className="infinity-parallax-layer infinity-paint-layer">
        <Image
          src="/images/infinite-canvas/paint-slab.png"
          alt=""
          width={1542}
          height={592}
          sizes="(min-width: 1280px) 340px, (min-width: 768px) 30vw, 52vw"
          className="infinity-paint-asset"
        />
      </div>
      <div className="infinity-parallax-layer infinity-printer-layer">
        <Image
          src="/images/infinite-canvas/printer-paper.png"
          alt=""
          width={802}
          height={969}
          priority
          sizes="(min-width: 1280px) 390px, (min-width: 768px) 34vw, 62vw"
          className="infinity-printer-asset"
        />
      </div>
      <div className="infinity-parallax-layer infinity-cursor-layer">
        <Image
          src="/images/infinite-canvas/magic-cursor.png"
          alt=""
          width={1417}
          height={880}
          priority
          sizes="(min-width: 1280px) 160px, (min-width: 768px) 18vw, 32vw"
          className="infinity-cursor-asset"
        />
      </div>
      <div className="infinity-parallax-layer infinity-palette-layer">
        <Image
          src="/images/infinite-canvas/moon-palette.png"
          alt=""
          width={924}
          height={1067}
          priority
          sizes="(min-width: 1280px) 150px, (min-width: 768px) 16vw, 26vw"
          className="infinity-palette-asset"
        />
      </div>
    </div>
  )
}

function InfinityMagicField() {
  return (
    <div className="pointer-events-none absolute inset-0 z-[2]" aria-hidden>
      {INFINITY_MAGIC_STARS.map((positionClassName, index) => (
        <span
          key={positionClassName}
          className={`infinity-magic-star absolute ${positionClassName}`}
          style={{ animationDelay: `${index * 0.55}s` }}
        />
      ))}
    </div>
  );
}
