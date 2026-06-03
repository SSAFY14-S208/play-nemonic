"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cva } from "class-variance-authority";

import { cn } from "@/shared/libs";

import type { AdminNavItem } from '../../constants';

const itemVariants = cva(
  "flex items-center gap-3 rounded-[var(--radius-md)] px-3 py-2 transition-colors",
  {
    variants: {
      active: {
        true: "bg-surface-default text-fg-primary body-l-b shadow-sm",
        false:
          "text-fg-inverse/85 hover:bg-white/10 hover:text-fg-inverse body-l-m",
      },
      depth: {
        root: "",
        child: "ml-9 py-1.5",
      },
    },
    defaultVariants: { active: false, depth: "root" },
  },
);

type AdminSidebarItemProps = {
  item: AdminNavItem;
  depth?: "root" | "child";
  onNavigate?: () => void;
};

export function AdminSidebarItem({
  item,
  depth = "root",
  onNavigate,
}: AdminSidebarItemProps) {
  const pathname = usePathname();
  const isActive =
    pathname === item.href || pathname.startsWith(`${item.href}/`);
  const Icon = item.icon;

  return (
    <Link
      href={item.href}
      onClick={onNavigate}
      aria-current={isActive ? "page" : undefined}
      className={cn(itemVariants({ active: isActive, depth }))}
    >
      {depth === "root" && (
        <span
          className={cn(
            "flex h-7 w-7 shrink-0 items-center justify-center rounded-full",
            isActive ? "bg-primary-5 text-primary-2" : "bg-white/15",
          )}
        >
          <Icon className="h-4 w-4" />
        </span>
      )}
      <span className="truncate">{item.label}</span>
    </Link>
  );
}
