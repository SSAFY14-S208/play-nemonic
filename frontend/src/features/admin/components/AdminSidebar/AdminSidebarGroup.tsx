"use client";

import type { AdminNavGroup } from '../../constants';
import { AdminSidebarAccordion } from "./AdminSidebarAccordion";
import { AdminSidebarItem } from "./AdminSidebarItem";

type AdminSidebarGroupProps = {
  group: AdminNavGroup;
};

export function AdminSidebarGroup({ group }: AdminSidebarGroupProps) {
  return (
    <div className="flex flex-col gap-2">
      <p className="caption-b px-3 text-fg-inverse/55 tracking-wider">
        {group.label}
      </p>
      <div className="flex flex-col gap-1">
        {group.items.map((item) =>
          item.children && item.children.length > 0 ? (
            <AdminSidebarAccordion key={item.key} item={item} />
          ) : (
            <AdminSidebarItem key={item.key} item={item} />
          ),
        )}
      </div>
    </div>
  );
}
