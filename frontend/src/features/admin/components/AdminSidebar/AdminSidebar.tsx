"use client";

import { useMemo } from "react";

import { useAdminAuthStore } from "@/shared/stores";

import { ADMIN_NAVIGATION } from "../../constants";
import type { AdminNavGroup } from "../../constants";
import { AdminSidebarGroup } from "./AdminSidebarGroup";

export function AdminSidebar() {
  const adminRole = useAdminAuthStore((state) => state.admin?.role ?? null);

  const visibleNavigation = useMemo<AdminNavGroup[]>(() => {
    return ADMIN_NAVIGATION.map((group) => ({
      ...group,
      items: group.items.filter(
        (item) => !item.requiredRole || item.requiredRole === adminRole,
      ),
    })).filter((group) => group.items.length > 0);
  }, [adminRole]);

  return (
    <aside className="flex w-64 shrink-0 flex-col gap-6 overflow-y-auto bg-primary-1 px-4 py-6">
      {visibleNavigation.map((group) => (
        <AdminSidebarGroup key={group.key} group={group} />
      ))}
    </aside>
  );
}
