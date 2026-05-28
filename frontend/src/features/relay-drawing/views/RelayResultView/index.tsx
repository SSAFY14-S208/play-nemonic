"use client";

import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";

import { cn } from "@/shared/libs";
import { writeCommunityCanvasHandoffDraft } from "@/shared/utils";
import RelayBgmToggle from "@/features/relay-drawing/components/RelayBgmToggle";
import RelayHowToPlayButton from "@/features/relay-drawing/components/RelayHowToPlayButton";
import { useRelayDrawingStore } from "@/features/relay-drawing/stores";

import { PhoneLauncherButton } from "@/shared/components";

import { useRelayResult, useRelayResultAutoCycle } from './hooks';
import { ResultRevealAnimation, ResultRightPanel } from './sections';

const PANEL_CARD_CLASS =
  "rounded-3xl bg-relay-paper px-6 py-5 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)] sm:px-8";
const CANVAS_BOX_HEIGHT_CLASS = "h-[min(60vh,520px)] lg:h-[min(72vh,720px)]";

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
    isDownloading,
    downloadActiveArtifact,
    isSharingExternal,
    canShareExternal,
    shareActiveArtifact,
  } = useRelayResult();

  useRelayResultAutoCycle({
    resultCount: resultItems.length,
    activeResultIndex,
    setActiveResultIndex,
  });

  const activeResultItem = resultItems[activeResultIndex] ?? null;
  const faceDrawerNickname =
    activeResultItem?.parts.find((partItem) => partItem.part === "FACE")?.drawerNickname ?? null;

  const handleCommunityPost = () => {
    const communityImageUrl = activeResultItem?.contentUrl ?? resultImageUrl;
    if (!communityImageUrl) return;

    writeCommunityCanvasHandoffDraft({
      sourceKind: "RELAY",
      title: faceDrawerNickname ? `${faceDrawerNickname}의 릴레이 드로잉` : "릴레이 드로잉",
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
      <div className="mx-auto flex min-h-screen w-full max-w-360 flex-col gap-4 px-4 py-6 sm:gap-6 sm:px-6 lg:gap-6 lg:px-[5%] lg:py-8">
        <div className="flex items-center justify-end gap-2 lg:hidden">
          <RelayHowToPlayButton className="size-11" />
          <RelayBgmToggle className="size-11" />
          <PhoneLauncherButton className="size-11" />
        </div>

        <main className="grid flex-1 grid-cols-1 gap-4 lg:grid-cols-[7fr_5fr] lg:items-stretch lg:gap-6">
          <div
            className={cn(
              PANEL_CARD_CLASS,
              "order-1 flex items-center justify-center lg:col-start-1",
            )}
          >
            {resultImageUrl ? (
              <div className="relative flex h-full w-full items-center justify-center rounded-[14px] border-[1.5px] border-relay-line bg-relay-background">
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

          <div className={cn(PANEL_CARD_CLASS, "order-2 lg:col-start-2")}>
            <ResultRightPanel
              resultItems={resultItems}
              activeResultIndex={activeResultIndex}
              onSelectResult={setActiveResultIndex}
              onReturnToLobby={handleReturnToLobby}
              onCommunityPost={handleCommunityPost}
              canPostCommunity={Boolean(resultImageUrl)}
              onShareExternal={shareActiveArtifact}
              isSharingExternal={isSharingExternal}
              canShareExternal={canShareExternal}
              onDownloadArtifact={downloadActiveArtifact}
              isDownloading={isDownloading}
              canDownload={Boolean(activeResultItem)}
            />
          </div>
        </main>
      </div>
    </section>
  );
}
