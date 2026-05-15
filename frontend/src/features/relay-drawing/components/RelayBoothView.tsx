"use client";

import { HelpCircle } from "lucide-react";
import { motion } from "motion/react";
import Image from "next/image";
import { useState } from "react";

import { HowToPlayModal } from "@/shared/components";

import relayDrawingTitle from "../assets/relay-drawing-title.png";
import { RELAY_HOW_TO_PLAY_PANELS } from "../constants";
import { useRelayBooth } from "../hooks";
import RelayBoothBackground from "./RelayBoothBackground";
import RelayBoothEntrance from "./RelayBoothEntrance";
import RelayButton from "./RelayButton";
import RelayJoinRoomModal from "./RelayJoinRoomModal";
import RelayNicknameModal from "./RelayNicknameModal";

// 익명 닉네임 상태에서 어떤 액션을 누르려 했는지 기억해뒀다가, 닉네임 모달이
// 닫힌 직후 자동으로 이어서 수행하기 위한 식별자.
type PendingBoothAction = "create" | "join" | null;

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
  const [isHowToPlayModalOpen, setIsHowToPlayModalOpen] = useState(false);
  // 닉네임 모달이 닫혀 store가 새 닉네임으로 갱신되면, 사용자가 원래 누르려 했던
  // 액션(방 만들기 / 방 입장 모달 열기)을 한 번만 자동으로 이어서 수행한다.
  const [pendingAction, setPendingAction] = useState<PendingBoothAction>(null);
  // 입장 인트로 애니메이션이 끝나면(또는 사용자가 클릭으로 스킵하면) 좌측 영역
  // (PostItNote + 설명 + 버튼)이 페이드 인되어 상호작용 가능 상태가 된다.
  const [isLeftRevealed, setIsLeftRevealed] = useState(false);

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
      <section className="relative isolate min-h-full overflow-auto bg-relay-background lg:overflow-hidden">
        {/* 데코 배경 — sparkle/squiggle/post-it 등 (lg+에서만 표시).
            인트로 시퀀스가 끝난 시점(좌측 페이드 인 트리거)에 함께 등장. */}
        <RelayBoothBackground isVisible={isLeftRevealed} />

        {/* 컨테이너 — 모바일/태블릿: 세로 stack, lg+: 가로 row.
            높이도 lg+에서만 고정(900px), 그 이하는 viewport 높이 기준으로 자연스럽게. */}
        <div className="mx-auto flex min-h-screen w-full max-w-2xl flex-col-reverse items-center justify-center gap-8 px-4 py-8 sm:px-6 lg:h-screen lg:min-h-0 lg:max-w-360 lg:flex-row lg:justify-between lg:gap-12 lg:px-[5%] lg:py-0">
          {/* 좌측 컬럼 — 안내 콘텐츠 */}
          <div className="flex w-full flex-1 py-8 lg:max-w-150 lg:py-12">
            <motion.div
              className="flex w-full flex-col gap-4 px-6 py-6 sm:gap-6 sm:px-10 sm:py-8 lg:px-12"
              initial={{ opacity: 0 }}
              animate={{ opacity: isLeftRevealed ? 1 : 0 }}
              transition={{ duration: 0.5, ease: "easeOut" }}
              style={{ pointerEvents: isLeftRevealed ? "auto" : "none" }}
            >
              {/* 두 줄 로고 PNG. viewport 폭에 따라 280→340→420px로 fluid 스케일. */}
              <h1 className="contents">
                <Image
                  src={relayDrawingTitle}
                  alt="우당탕 릴레이 드로잉"
                  priority
                  className="h-auto w-full max-w-70 sm:max-w-85 lg:max-w-105"
                />
              </h1>
              <div className="body-l-r flex flex-col text-relay-ink">
                <p>얼굴 → 몸통 → 다리, 3라운드.</p>
                <p>캔버스가 다음 사람에게 넘어가요.</p>
                <p>이전 사람 그림의 하단 일부 힌트만 보고</p>
                <p>이어 그리면 결과는 우당탕 캐릭터!</p>
              </div>
              {/* 좁은 화면에선 버튼이 column으로 stack, sm+ 부터 row로 나란히. */}
              <div className="flex flex-col gap-3 sm:flex-row">
                <RelayButton
                  onClick={handleCreateClick}
                  disabled={isActionDisabled}
                  size="lg"
                  shape="roundedLg"
                  className="px-8 shadow-[0_6px_16px_rgba(184,121,22,0.3)]"
                >
                  {isPending ? "방 만드는 중…" : "방 만들기 →"}
                </RelayButton>
                <RelayButton
                  onClick={handleJoinClick}
                  disabled={isActionDisabled}
                  variant="secondary"
                  size="lg"
                  shape="roundedLg"
                  className="border-2"
                >
                  방 입장
                </RelayButton>
                <RelayButton
                  onClick={() => setIsHowToPlayModalOpen(true)}
                  variant="secondary"
                  size="lg"
                  shape="roundedLg"
                  className="gap-1.5"
                >
                  <HelpCircle className="size-5" aria-hidden />
                  게임 설명
                </RelayButton>
              </div>
              {error && !isJoinModalOpen && (
                <p role="alert" className="caption-r text-error">
                  {error}
                </p>
              )}
            </motion.div>
          </div>

          {/* 우측 컬럼 — 마술사 아트워크 */}
          <div className="flex w-full items-center justify-center py-4 lg:max-w-125 lg:flex-1 lg:py-0">
            <RelayBoothEntrance onLeftReveal={() => setIsLeftRevealed(true)} />
          </div>
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
      <HowToPlayModal
        open={isHowToPlayModalOpen}
        onOpenChange={setIsHowToPlayModalOpen}
        panels={RELAY_HOW_TO_PLAY_PANELS}
        accentColor="var(--color-relay-accent)"
      />
    </>
  );
}
