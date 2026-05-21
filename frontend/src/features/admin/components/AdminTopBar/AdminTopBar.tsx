"use client";

import { AdminAccountPopover } from "./AdminAccountPopover";
import { AdminNotificationButton } from "./AdminNotificationButton";
import { AdminNotificationModal } from "./AdminNotificationModal";

export function AdminTopBar() {
  return (
    <header className="flex h-16 shrink-0 items-center justify-between border-b border-border-default bg-surface-default">
      <div className="flex h-full w-64 items-center gap-2 bg-primary-1 px-6">
        <span className="h4-b text-fg-inverse">Play! Nemonic</span>
        <span className="caption-b text-fg-inverse/70">ADMIN</span>
      </div>
      <div className="flex items-center gap-2 px-6">
        <AdminNotificationButton />
        <AdminAccountPopover />
      </div>
      <AdminNotificationModal />
    </header>
  );
}
