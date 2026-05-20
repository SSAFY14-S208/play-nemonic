"use client";

import { Smartphone } from "lucide-react";

import { cn } from "@/shared/libs";
import { usePhoneLauncherStore } from "@/shared/stores";

/** 인라인으로 배치되는 phone 열기 버튼.
 *  shared/stores/phoneLauncherStore를 통해 PhoneLauncher의 openPhone을 호출한다.
 *
 *  @param className    - 버튼 외곽 크기 제어 (e.g. `size-11`)
 *  @param iconClassName - Lucide 아이콘 크기 제어 (e.g. `size-6`) */
export default function PhoneLauncherButton({
  className,
  iconClassName,
}: {
  className?: string;
  iconClassName?: string;
}) {
  const requestOpen = usePhoneLauncherStore((state) => state.requestOpen);

  return (
    <button
      type="button"
      onClick={requestOpen}
      aria-label="핸드폰 열기"
      title="핸드폰"
      className={cn(
        "inline-flex size-16 cursor-pointer items-center justify-center rounded-[0.8rem] border border-[rgb(255_247_235/0.74)] bg-[rgb(255_250_242/0.72)] shadow-[0_0.8rem_2rem_rgb(54_45_80/0.12)] backdrop-blur-[16px] transition duration-[160ms] ease-out hover:-translate-y-px hover:bg-[rgb(255_252_247/0.88)] active:scale-[0.96]",
        className,
      )}
    >
      <Smartphone
        className={cn("size-5 text-[#5b4a82]", iconClassName)}
        strokeWidth={2.35}
      />
    </button>
  );
}
