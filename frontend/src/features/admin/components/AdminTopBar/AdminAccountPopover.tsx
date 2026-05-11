"use client";

import { Popover } from "@base-ui/react/popover";
import { ChevronDown, LogOut } from "lucide-react";

import { useAdminAuthStore } from "@/shared/stores";

import { useAdminLogout } from "../../hooks";

export function AdminAccountPopover() {
  const adminEmail = useAdminAuthStore((state) => state.admin?.email ?? null);
  const { isPending, logout } = useAdminLogout();

  return (
    <Popover.Root>
      <Popover.Trigger className="flex items-center gap-2 rounded-[var(--radius-md)] px-3 py-2 transition-colors hover:bg-surface-subtle">
        <span className="body-m text-fg-primary">{adminEmail ?? "—"}</span>
        <ChevronDown className="h-4 w-4 text-fg-secondary" />
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Positioner sideOffset={8} align="end">
          <Popover.Popup className="z-[var(--z-dropdown)] w-48 overflow-hidden rounded-[var(--radius-md)] border border-border-default bg-surface-default shadow-md">
            <button
              type="button"
              onClick={logout}
              disabled={isPending}
              className="body-m flex w-full items-center gap-2 px-4 py-2.5 text-left text-fg-primary transition-colors hover:bg-surface-subtle disabled:opacity-50"
            >
              <LogOut className="h-4 w-4" />
              {isPending ? "로그아웃 중…" : "로그아웃"}
            </button>
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  );
}
