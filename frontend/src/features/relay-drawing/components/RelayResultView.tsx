"use client";

import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";

import { useRelayResult } from "../hooks";
import { useRelayDrawingStore } from "../stores";
import { cn } from "@/shared/libs";

import { ResultRevealAnimation, ResultRightPanel } from "./result-view";

const PANEL_CARD_CLASS =
  "rounded-3xl bg-relay-paper px-6 py-5 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)] sm:px-8";

// 좌측 캔버스 박스 — 화면 세로 비율 기준 cap. 848:1920(세로형) 비율을 유지해
// 우측 패널 자연 높이에 근사한 크기로 들어간다. 너비는 aspect-ratio에서 자동 도출.
const CANVAS_BOX_HEIGHT_CLASS = "h-[min(60vh,520px)] lg:h-[min(72vh,720px)]";

// roomStatus === 'FINISHED' 일 때 RelayRoomPage가 렌더한다.
// 좌측: 자동 카메라 메타포 인터렉션(face/body/legs 줌인 → 머지 → 빛 → 최종 합성).
// 우측: face-drawer(앨범 소유자) 닉네임 버튼 + 보관함/광장/로비 액션 버튼.
//
// sub-components는 ./result-view/ 폴더에 분리. 이 파일은 데이터 플로우와
// 레이아웃 조립만 담당한다.
export default function RelayResultView() {
  const router = useRouter();
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom);
  const {
    resultItems,
    activeResultIndex,
    setActiveResultIndex,
    resultImageUrl,
    segments,
    isHost,
    closeRoom,
  } = useRelayResult();

  // "로비로 돌아가기" — 호스트면 방 종료까지 같이 처리한 뒤 부스로 이동.
  // roomStatus가 단방향(FINISHED→WAITING 전환 API 없음)이라 같은 방의
  // 대기 로비로는 못 돌아가므로, 새 방을 만들거나 다른 방에 입장할 수 있는
  // 부스(/relay-drawing)가 의미상 가장 가까운 "로비".
  // closeRoom은 fire-and-forget — API/WS 결과를 기다리지 않고 즉시 화면 정리 후
  // 부스로 이동한다. 다른 참가자에게 ROOM_CLOSED는 백엔드/WS가 책임진다.
  const handleReturnToLobby = () => {
    if (isHost) closeRoom();
    clearRoom();
    router.push("/relay-drawing");
  };

  return (
    <section className="relative isolate min-h-full">
      <div className="mx-auto flex min-h-screen w-full max-w-360 flex-col gap-4 px-4 py-6 sm:gap-6 sm:px-6 lg:gap-6 lg:px-[5%] lg:py-8">
        <main className="grid flex-1 grid-cols-1 gap-4 lg:grid-cols-[7fr_5fr] lg:items-stretch lg:gap-6">
          {/* ① 좌측 — 자동 카메라 인터렉션 영역. */}
          <div
            className={cn(
              PANEL_CARD_CLASS,
              "order-1 flex items-center justify-center lg:col-start-1",
            )}
          >
            {resultImageUrl ? (
              <div className="w-full h-full relative flex items-center justify-center rounded-[14px] border-[1.5px] border-relay-line bg-relay-background">
                <ResultRevealAnimation
                  resultImageUrl={resultImageUrl}
                  segments={segments}
                  replayKey={activeResultIndex}
                  className={CANVAS_BOX_HEIGHT_CLASS}
                />
              </div>
            ) : (
              <div
                className={cn(
                  CANVAS_BOX_HEIGHT_CLASS,
                  "flex items-center justify-center rounded-[14px] border-[1.5px] border-relay-line bg-relay-background",
                )}
                style={{ aspectRatio: "848 / 1920" }}
                role="status"
              >
                <Loader2
                  aria-label="결과 이미지를 불러오는 중"
                  className="size-10 animate-spin text-relay-accent-strong"
                />
              </div>
            )}
          </div>

          {/* ② 우측 — face-drawer 버튼 + 액션 버튼 통합. */}
          <div className={cn(PANEL_CARD_CLASS, "order-2 lg:col-start-2")}>
            <ResultRightPanel
              resultItems={resultItems}
              activeResultIndex={activeResultIndex}
              onSelectResult={setActiveResultIndex}
              isHost={isHost}
              onReturnToLobby={handleReturnToLobby}
            />
          </div>
        </main>
      </div>
    </section>
  );
}
