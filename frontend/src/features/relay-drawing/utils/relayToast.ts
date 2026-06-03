import type { CSSProperties } from "react";
import { toast, type ExternalToast } from "sonner";

// 릴레이 드로잉 전용 토스트 스타일.
// RelayButton primary 변형(bg-relay-accent + text-relay-ink)과 톤을 맞춘다.
const RELAY_TOAST_STYLE: CSSProperties = {
  background: "#FFFFFF",
  color: "var(--color-relay-ink)",
  border: "1px solid var(--color-relay-accent)",
  fontFamily: "var(--font-paperlogy)",
  fontWeight: 700,
  fontSize: "14px",
  lineHeight: "20px",
  borderRadius: "12px",
};

function mergeOptions(options?: ExternalToast): ExternalToast {
  return {
    ...options,
    style: { ...RELAY_TOAST_STYLE, ...options?.style },
  };
}

/**
 * 릴레이 드로잉 전용 toast wrapper.
 * admin 등 다른 feature의 Sonner 기본 스타일에 영향을 주지 않으면서
 * relay 테마에 맞는 토스트를 띄운다.
 */
export function relayToast(message: string, options?: ExternalToast) {
  return toast(message, mergeOptions(options));
}

relayToast.success = (message: string, options?: ExternalToast) =>
  toast.success(message, mergeOptions(options));

relayToast.error = (message: string, options?: ExternalToast) =>
  toast.error(message, mergeOptions(options));
