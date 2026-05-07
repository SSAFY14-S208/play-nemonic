"use client";

import { Popover } from "@base-ui/react/popover";
import { ChevronDown, LogIn, LogOut } from "lucide-react";

const ADMIN_EMAIL = "test1@admin.nemonic.world";

export function AdminAccountPopover() {
  return (
    <Popover.Root>
      <Popover.Trigger className="flex items-center gap-2 rounded-[var(--radius-md)] px-3 py-2 transition-colors hover:bg-surface-subtle">
        <span className="body-m text-fg-primary">{ADMIN_EMAIL}</span>
        <ChevronDown className="h-4 w-4 text-fg-secondary" />
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Positioner sideOffset={8} align="end">
          <Popover.Popup className="z-[var(--z-dropdown)] w-48 overflow-hidden rounded-[var(--radius-md)] border border-border-default bg-surface-default shadow-md">
            <button
              type="button"
              onClick={() => console.log("[admin] login")}
              className="body-m flex w-full items-center gap-2 px-4 py-2.5 text-left text-fg-primary transition-colors hover:bg-surface-subtle"
            >
              <LogIn className="h-4 w-4" />
              로그인
            </button>
            <button
              type="button"
              onClick={() => console.log("[admin] logout")}
              className="body-m flex w-full items-center gap-2 px-4 py-2.5 text-left text-fg-primary transition-colors hover:bg-surface-subtle"
            >
              <LogOut className="h-4 w-4" />
              로그아웃
            </button>
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  );
}
