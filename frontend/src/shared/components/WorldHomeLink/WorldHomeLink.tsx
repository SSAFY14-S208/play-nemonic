"use client";

import { useCallback, useState } from "react";
import Image from "next/image";
import { usePathname, useRouter } from "next/navigation";
import { motion } from "motion/react";
import LeaveConfirmModal from "./LeaveConfirmModal";

const WORLD_PATH = "/";

/** 클릭 시 진행 내용을 잃을 수 있어 떠나기 전 확인 모달을 띄우는 라우트 */
const CONFIRM_BEFORE_LEAVE_ROUTES = [
  "/flipbook",
  "/relay-drawing",
  "/community-canvas",
  "/fortune",
];

/** 월드 홈 링크 자체를 표시하지 않는 라우트 (이동할 곳이 자기 자신이거나 인트로 화면) */
const HIDDEN_ROUTES = new Set([WORLD_PATH]);

interface WorldHomeLinkProps {
  /** confirm 모달의 취소/확인 버튼 색상을 페이지 테마에 맞게 덮어쓰고 싶을 때 전달합니다. */
  leaveConfirmCancelButtonClassName?: string;
  leaveConfirmConfirmButtonClassName?: string;
}

export default function WorldHomeLink({
  leaveConfirmCancelButtonClassName,
  leaveConfirmConfirmButtonClassName,
}: WorldHomeLinkProps = {}) {
  const router = useRouter();
  const pathname = usePathname();
  const [confirmOpen, setConfirmOpen] = useState(false);

  const shouldConfirmBeforeLeave = CONFIRM_BEFORE_LEAVE_ROUTES.some((prefix) =>
    pathname.startsWith(prefix),
  );

  const handleClick = useCallback(() => {
    if (shouldConfirmBeforeLeave) {
      setConfirmOpen(true);
      return;
    }
    router.push(WORLD_PATH);
  }, [shouldConfirmBeforeLeave, router]);

  const handleConfirm = useCallback(() => {
    setConfirmOpen(false);
    router.push(WORLD_PATH);
  }, [router]);

  const handleCancel = useCallback(() => {
    setConfirmOpen(false);
  }, []);

  if (HIDDEN_ROUTES.has(pathname)) {
    return null;
  }

  return (
    <>
      <motion.button
        type="button"
        onClick={handleClick}
        aria-label="네모닉 룸으로 돌아가기"
        className="fixed left-4 top-4 z-[var(--z-sticky)] flex items-center justify-center p-1 sm:left-6 sm:top-6"
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: "spring", stiffness: 320, damping: 24 }}
        whileHover={{ scale: 1.05, y: -2 }}
        whileTap={{ scale: 0.96 }}
      >
        <Image
          src="/images/play-nemonic-logo.png"
          alt="Play! Nemonic"
          width={1672}
          height={941}
          priority
          draggable={false}
          className="h-auto w-[7.5rem] drop-shadow-[0_8px_18px_rgba(0,0,0,0.18)] sm:w-[8.75rem]"
        />
      </motion.button>
      <LeaveConfirmModal
        open={confirmOpen}
        onCancel={handleCancel}
        onConfirm={handleConfirm}
        cancelButtonClassName={leaveConfirmCancelButtonClassName}
        confirmButtonClassName={leaveConfirmConfirmButtonClassName}
      />
    </>
  );
}
