"use client";

import { ADMIN_NAVIGATION } from "../../constants";
import { AdminSidebarGroup } from "./AdminSidebarGroup";

export function AdminSidebar() {
  return (
    <aside className="flex w-64 shrink-0 flex-col gap-6 overflow-y-auto bg-primary-1 px-4 py-6">
      {ADMIN_NAVIGATION.map((group) => (
        <AdminSidebarGroup key={group.key} group={group} />
      ))}
    </aside>
  );
}
