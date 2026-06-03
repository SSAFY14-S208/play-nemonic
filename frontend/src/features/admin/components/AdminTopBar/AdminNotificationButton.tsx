"use client";

import { Bell } from "lucide-react";

import { cn } from "@/shared/libs";

import { useNotificationStore } from '../../stores';

export function AdminNotificationButton() {
  const { hasUnread, openModal } = useNotificationStore();

  return (
    <button
      type="button"
      onClick={openModal}
      aria-label="알림 열기"
      className={cn(
        "relative flex h-10 w-10 items-center justify-center rounded-full",
        "text-fg-secondary transition-colors hover:bg-surface-subtle hover:text-fg-primary",
      )}
    >
      <Bell className="h-5 w-5" />
      {hasUnread && (
        <span
          aria-hidden
          className="absolute bottom-1.5 right-1.5 h-2 w-2 rounded-full bg-[#ef4444] ring-2 ring-surface-default"
        />
      )}
    </button>
  );
}
