import Image from "next/image";
import { phoneClose } from "@/shared/assets";
import { PHONE_CLOSE_BUTTON_LAYOUT } from "../constants";

interface PhoneCloseButtonProps {
  onClose: () => void;
}

export function PhoneCloseButton({ onClose }: PhoneCloseButtonProps) {
  return (
    <button
      type="button"
      aria-label="휴대폰 닫기"
      onClick={onClose}
      className="absolute cursor-pointer z-40 transition hover:scale-[1.02] focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary-2 max-sm:hidden"
      style={PHONE_CLOSE_BUTTON_LAYOUT}
    >
      <Image
        src={phoneClose}
        alt="휴대폰 닫기 버튼 아이콘"
        aria-hidden
        fill
        className="object-contain"
        sizes="90px"
      />
    </button>
  );
}
