"use client";

import { useState } from "react";

import { useRelayBooth } from "../hooks";
import RelayArtworkCard from "./RelayArtworkCard";
import RelayJoinRoomModal from "./RelayJoinRoomModal";
import RelayNicknameModal from "./RelayNicknameModal";
import { cn } from "@/shared/libs";
import { PostItNote } from "@/shared/components";

// 익명 닉네임 상태에서 어떤 액션을 누르려 했는지 기억해뒀다가, 닉네임 모달이
// 닫힌 직후 자동으로 이어서 수행하기 위한 식별자.
type PendingBoothAction = "create" | "join" | null;

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
  const {
    isUserReady,
    needsNicknameSetup,
    isPending,
    error,
    createRoom,
    joinRoom,
    clearError,
  } = useRelayBooth();
  const [isJoinModalOpen, setIsJoinModalOpen] = useState(false);
  const [isNicknameModalOpen, setIsNicknameModalOpen] = useState(false);
  // 닉네임 모달이 닫혀 store가 새 닉네임으로 갱신되면, 사용자가 원래 누르려 했던
  // 액션(방 만들기 / 방 입장 모달 열기)을 한 번만 자동으로 이어서 수행한다.
  const [pendingAction, setPendingAction] = useState<PendingBoothAction>(null);

  // 모달 외부 에러는 부스 화면 하단에 띄우고, 모달이 열려 있을 때는 모달 내부에서만
  // 노출되도록 분리한다. 모달이 닫히면 양쪽 모두 깨끗하게 클리어.
  const handleJoinModalChange = (open: boolean) => {
    setIsJoinModalOpen(open);
    if (!open) clearError();
  };

  const handleCreateClick = () => {
    if (needsNicknameSetup) {
      setPendingAction("create");
      setIsNicknameModalOpen(true);
      return;
    }
    createRoom();
  };

  const handleJoinClick = () => {
    if (needsNicknameSetup) {
      setPendingAction("join");
      setIsNicknameModalOpen(true);
      return;
    }
    setIsJoinModalOpen(true);
  };

  const handleNicknameSuccess = () => {
    // 모달 자체는 RelayNicknameModal 내부에서 onOpenChange(false)로 닫는다.
    // 여기서는 보류했던 액션만 이어서 수행.
    if (pendingAction === "create") {
      createRoom();
    } else if (pendingAction === "join") {
      setIsJoinModalOpen(true);
    }
    setPendingAction(null);
  };

  const handleNicknameModalChange = (open: boolean) => {
    setIsNicknameModalOpen(open);
    // 사용자가 닉네임 변경 없이 모달을 닫으면 보류 액션도 폐기한다.
    if (!open) setPendingAction(null);
  };

  const isActionDisabled = !isUserReady || isPending;

  return (
    <>
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
              onClick={handleCreateClick}
              disabled={isActionDisabled}
              className="body-b min-h-[56px] rounded-[16px] bg-relay-accent px-8 text-relay-ink shadow-[0_6px_16px_rgba(184,121,22,0.3)] disabled:opacity-45"
            >
              {isPending ? "방 만드는 중…" : "방 만들기 →"}
            </button>
            <button
              type="button"
              onClick={handleJoinClick}
              disabled={isActionDisabled}
              className="body-b min-h-[56px] rounded-[16px] border-2 border-relay-line bg-relay-paper px-7 text-relay-accent-strong disabled:opacity-45"
            >
              방 입장
            </button>
          </div>
          {error && !isJoinModalOpen && (
            <p role="alert" className="caption-r mt-3 text-error">
              {error}
            </p>
          )}
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

    <RelayJoinRoomModal
      open={isJoinModalOpen}
      onOpenChange={handleJoinModalChange}
      onSubmit={joinRoom}
      isPending={isPending}
      error={error}
    />
    <RelayNicknameModal
      open={isNicknameModalOpen}
      onOpenChange={handleNicknameModalChange}
      onSuccess={handleNicknameSuccess}
    />
    </>
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
