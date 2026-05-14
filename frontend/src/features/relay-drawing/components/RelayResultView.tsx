"use client";

import { useRouter } from "next/navigation";

import { RELAY_RESULT_ACTIONS } from "../constants";
import { useRelayResult } from "../hooks";
import { useRelayDrawingStore } from "../stores";
import { cn } from "@/shared/libs";
import { writeCommunityCanvasHandoffDraft } from "@/shared/utils";

import RelayButton from "./RelayButton";
import {
  ResultAlbumsPanel,
  ResultCanvas,
  ResultCreditsPanel,
  ResultProgressStrip,
  ResultStageHeader,
  ResultStepNav,
} from "./result-view";

const PANEL_CARD_CLASS =
  "rounded-3xl bg-relay-paper px-6 py-5 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)] sm:px-8";

// roomStatus === 'FINISHED' 일 때 RelayRoomPage가 렌더한다.
// 결과 단계는 useRelayResult 훅이 들고 있는 reveal 인덱스로 분기:
//   face → body → legs → final 순서로 단계별 작성자/캔버스를 보여준다.
//
// sub-components는 ./result-view/ 폴더에 분리. 이 파일은 데이터 플로우와
// 레이아웃 조립만 담당한다.
export default function RelayResultView() {
  const router = useRouter();
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom);
  const {
    // Reveal navigation
    reveals,
    activeReveal,
    activeRevealIndex,
    isFinalReveal,
    canShowPreviousResultReveal,
    canShowNextResultReveal,
    goToNextResultReveal,
    goToPreviousResultReveal,

    // Result data
    resultItems,
    activeResultIndex,
    setActiveResultIndex,
    resultImageUrl,
    segments,
    participantCount,
    ownerNickname,
    completedAtLabel,

    // Host action — fire-and-forget로 백엔드 close 호출만 수행
    isHost,
    closeRoom,

    // Fallback
    roundLines,
  } = useRelayResult();

  // "로비로 돌아가기" — 호스트면 방 종료까지 같이 처리한 뒤 부스로 이동.
  // roomStatus가 단방향(FINISHED→WAITING 전환 API 없음)이라 같은 방의
  // 대기 로비로는 못 돌아가므로, 새 방을 만들거나 다른 방에 입장할 수 있는
  // 부스(/relay-drawing)가 의미상 가장 가까운 "로비".
  // closeRoom은 fire-and-forget — API/WS 결과를 기다리지 않고 즉시 화면 정리 후
  // 부스로 이동한다. 다른 참가자에게 ROOM_CLOSED는 백엔드/WS가 책임진다.
  const activeResultItem = resultItems[activeResultIndex] ?? null;

  const handleCommunityPost = () => {
    const communityImageUrl = activeResultItem?.contentUrl ?? resultImageUrl;
    if (!communityImageUrl) return;

    writeCommunityCanvasHandoffDraft({
      sourceKind: "RELAY",
      title: ownerNickname ? `${ownerNickname}의 릴레이 드로잉` : "릴레이 드로잉",
      imageUrl: communityImageUrl,
      thumbnailUrl: activeResultItem?.thumbnailUrl ?? communityImageUrl,
      sourceGalleryId: activeResultItem?.galleryId ?? null,
      sourceContentKind: "relay_drawing",
    });
    router.push("/community-canvas");
  };

  const handleReturnToLobby = () => {
    if (isHost) closeRoom();
    clearRoom();
    router.push("/relay-drawing");
  };

  return (
    <section className="relative isolate min-h-full">
      <div className="mx-auto flex min-h-screen w-full max-w-360 flex-col gap-4 px-4 py-6 sm:gap-6 sm:px-6 lg:gap-4 lg:px-[5%] lg:py-6">
        <ResultProgressStrip
          activeReveal={activeReveal}
          activeRevealIndex={activeRevealIndex}
          reveals={reveals}
          completedAtLabel={completedAtLabel}
          ownerNickname={ownerNickname}
        />

        {/* 메인 grid — 모바일은 1열 stack(order-{n}), lg+는 좌측 Canvas(2행 전체 높이)
            + 우측 Credits/Albums 상하 stack. viewport 잠금을 두지 않아 콘텐츠가
            늘어나면 자연스럽게 페이지 스크롤로 밀려난다. */}
        <main className="grid flex-1 grid-cols-1 gap-4 lg:grid-cols-[7fr_5fr] lg:grid-rows-[1fr_auto] lg:gap-4">
          {/* ② Canvas — 좌측 메인 영역. 캔버스가 RELAY_STAGE_SIZE(848:720) 비율로
              고정돼 reveal 전환에도 같은 dimensions을 유지하므로, row-span으로 우측
              컬럼 전체 높이를 끌어다 채울 필요가 없다. self-start로 위쪽 정렬만 잡고
              컨텐츠 높이 그대로 둔다. */}
          <div
            className={cn(
              PANEL_CARD_CLASS,
              "order-1 flex flex-col gap-3 lg:col-start-1 lg:row-start-1 lg:row-span-2 lg:self-start",
            )}
          >
            {!isFinalReveal && (
              <ResultStageHeader activeReveal={activeReveal} />
            )}

            <ResultCanvas
              activeReveal={activeReveal}
              roundLines={roundLines}
              resultImageUrl={resultImageUrl}
              segments={segments}
            />

            {!isFinalReveal && (
              <ResultStepNav
                activeReveal={activeReveal}
                activeRevealIndex={activeRevealIndex}
                revealCount={reveals.length}
                canShowPreviousResultReveal={canShowPreviousResultReveal}
                canShowNextResultReveal={canShowNextResultReveal}
                onShowPreviousResultReveal={goToPreviousResultReveal}
                onShowNextResultReveal={goToNextResultReveal}
              />
            )}
          </div>

          {/* ③ Credits — 우측 상단 */}
          <div
            className={cn(
              PANEL_CARD_CLASS,
              "order-2 lg:col-start-2 lg:row-start-1",
            )}
          >
            <ResultCreditsPanel
              activeReveal={activeReveal}
              activeRevealIndex={activeRevealIndex}
              isFinalReveal={isFinalReveal}
              segments={segments}
              participantCount={participantCount}
              ownerNickname={ownerNickname}
            />
          </div>

          {/* ④ Albums — 우측 하단 */}
          <div
            className={cn(
              PANEL_CARD_CLASS,
              "order-3 lg:col-start-2 lg:row-start-2",
            )}
          >
            <ResultAlbumsPanel
              resultItems={resultItems}
              activeResultIndex={activeResultIndex}
              onSelectResult={setActiveResultIndex}
            />
          </div>
        </main>

        {/* ⑤ 액션 카드 — 단계와 무관하게 항상 노출. 호스트/게스트 동일하게 3개 버튼
            (보관함에 / 광장에 전시하기 / 로비로 돌아가기). 호스트의 방 종료는
            "로비로 돌아가기" 핸들러 안에서 함께 처리된다. */}
        <div
          className={cn(
            PANEL_CARD_CLASS,
            "grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3",
          )}
        >
          {RELAY_RESULT_ACTIONS.map(({ label, Icon }, index) => {
            const isCommunityPostAction = index === 1;

            return (
              <RelayButton
                key={label}
                variant={index === 0 ? "secondary" : "primary"}
                size="md"
                onClick={isCommunityPostAction ? handleCommunityPost : undefined}
                disabled={isCommunityPostAction && !resultImageUrl}
                className={cn(
                  "gap-2 rounded-[14px] border-[1.5px]",
                  index === 0
                    ? "border-relay-line"
                    : "border-relay-accent shadow-[0_4px_10px_rgba(212,156,31,0.18)]",
                )}
              >
                <Icon className="size-4" aria-hidden />
                {label}
              </RelayButton>
            );
          })}
          <RelayButton
            onClick={handleReturnToLobby}
            size="md"
            className="rounded-[14px] border-[1.5px] border-relay-accent shadow-[0_4px_10px_rgba(212,156,31,0.18)]"
          >
            {isHost ? "방 종료" : "로비로 돌아가기"}
          </RelayButton>
        </div>
      </div>
    </section>
  );
}
