"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import { AnimatePresence, motion } from "motion/react";
import { ChevronDown } from "lucide-react";

import { cn } from "@/shared/libs";

import type { AdminNavItem } from '../../constants';
import { AdminSidebarItem } from "./AdminSidebarItem";

type AdminSidebarAccordionProps = {
  item: AdminNavItem;
};

export function AdminSidebarAccordion({ item }: AdminSidebarAccordionProps) {
  const pathname = usePathname();
  const Icon = item.icon;

  const isOnChildRoute = (item.children ?? []).some(
    (child) =>
      pathname === child.href || pathname.startsWith(`${child.href}/`),
  );

  const [isOpen, setIsOpen] = useState(() => isOnChildRoute);

  return (
    <div className="flex flex-col">
      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        aria-expanded={isOpen}
        className={cn(
          "flex items-center gap-3 rounded-[var(--radius-md)] px-3 py-2 transition-colors",
          "text-fg-inverse/85 body-l-m hover:bg-white/10 hover:text-fg-inverse",
        )}
      >
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white/15">
          <Icon className="h-4 w-4" />
        </span>
        <span className="flex-1 truncate text-left">{item.label}</span>
        <ChevronDown
          className={cn(
            "h-4 w-4 shrink-0 transition-transform duration-200",
            isOpen && "rotate-180",
          )}
        />
      </button>

      <AnimatePresence initial={false}>
        {isOpen && (
          <motion.div
            key="accordion-children"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: "easeInOut" }}
            className="overflow-hidden"
          >
            <div className="mt-1 flex flex-col gap-1 pb-1">
              {item.children?.map((child) => (
                <AdminSidebarItem
                  key={child.key}
                  item={child}
                  depth="child"
                  onNavigate={() => setIsOpen(true)}
                />
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
