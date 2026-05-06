"use client";

import RelayArtworkCard from "./RelayArtworkCard";
import { useRelayDrawingStore } from "../relayDrawingStore";
import { cn } from "@/shared/libs";
import { PostItNote } from "@/shared/components";

const FLOATING_PAPER_STYLES = [
  "left-[48.9%] top-[15.4%] h-11 w-14 rotate-[20deg] opacity-60",
  "left-[93%] top-[19.8%] h-10 w-12 -rotate-[25deg] opacity-50",
  "left-[95.1%] top-[53.2%] h-[38px] w-12 rotate-[15deg] opacity-50",
  "left-[51.3%] top-[78%] h-[42px] w-[54px] -rotate-[18deg] opacity-60",
  "left-[72%] top-[40%] h-8 w-[70px] -rotate-[13deg] opacity-60",
  "left-[82.2%] top-[24.3%] h-8 w-[70px] rotate-[16deg] opacity-60",
  "left-[57.6%] top-[23.7%] h-8 w-[70px] rotate-[6deg] opacity-60",
  "left-[84.9%] top-[79.9%] h-9 w-[46px] rotate-[10deg] opacity-50",
] as const;

export default function RelayBoothView() {
  const goToNextStep = useRelayDrawingStore((state) => state.goToNextStep);

  return (
    <section className="relative h-full overflow-hidden border border-relay-border bg-relay-background">
      <div className="relative mx-auto h-[900px] w-full max-w-[1440px] overflow-hidden">
        <PostItNote
          title="안내 문구를 담은 노란 포스트잇 배경 이미지"
          className="absolute left-[5.8%] top-[18%] h-[61.5%] w-[40.3%] text-[#FFE787]"
        />

        <div className="absolute left-[9.7%] top-[28.7%] w-[33%]">
          <span className="body-b inline-flex min-h-[43px] items-center rounded-full bg-relay-active px-5 text-relay-accent-strong">
            2~6명
          </span>
          <h1
            className="mt-4 whitespace-nowrap text-relay-ink"
            style={{ fontSize: "49px", fontWeight: 700, lineHeight: "78px" }}
          >
            우당탕 릴레이 드로잉
          </h1>
          <div className="body-l-r mt-6 text-relay-ink">
            <p>얼굴 → 몸통 → 다리, 3라운드.</p>
            <p>캔버스가 다음 사람에게 넘어가요.</p>
            <p>이전 사람 그림의 하단 일부 힌트만 보고</p>
            <p>이어 그리면 결과는 우당탕 캐릭터!</p>
          </div>
          <div className="mt-7 flex gap-3">
            <button
              type="button"
              onClick={goToNextStep}
              className="body-b min-h-[56px] rounded-[16px] bg-relay-accent px-8 text-relay-ink shadow-[0_6px_16px_rgba(184,121,22,0.3)]"
            >
              방 만들기 →
            </button>
            <button
              type="button"
              onClick={goToNextStep}
              className="body-b min-h-[56px] rounded-[16px] border-2 border-relay-line bg-relay-paper px-7 text-relay-accent-strong"
            >
              방 입장
            </button>
          </div>
        </div>

        <div className="absolute left-[47.8%] top-[19.9%] h-[64.5%] w-[47.2%] rotate-[3deg] rounded-[8px] bg-relay-pink/40 shadow-[0_16px_32px_rgba(184,121,22,0.2)]" />

        {FLOATING_PAPER_STYLES.map((floatingPaperStyle) => (
          <FloatingPaper
            key={floatingPaperStyle}
            className={floatingPaperStyle}
          />
        ))}

        <div className="absolute left-[50.7%] top-[24.9%] w-[13%] rotate-[6.85deg]">
          <RelayArtworkCard character="left" />
        </div>
        <div className="absolute left-[62.8%] top-[41%] z-10 w-[13%] rotate-[11.63deg]">
          <RelayArtworkCard character="center" />
        </div>
        <div className="absolute left-[78.7%] top-[26.3%] w-[13%] -rotate-[9.65deg]">
          <RelayArtworkCard character="right" />
        </div>

        <span className="absolute left-[90.2%] top-[38.8%] rotate-[25deg] text-[48px] opacity-70">
          ✏️
        </span>
      </div>
    </section>
  );
}

function FloatingPaper({ className }: { className: string }) {
  return (
    <span
      className={cn(
        "absolute rounded-[4px] bg-relay-paper shadow-[0_4px_8px_rgba(184,121,22,0.15)]",
        className,
      )}
    />
  );
}
