"use client";

import { usePathname } from "next/navigation";

import { findActiveAdminNavItem } from "../../constants";

export function AdminPageHeader() {
  const pathname = usePathname();
  const item = findActiveAdminNavItem(pathname);
  if (!item) return null;

  return (
    <header className="flex flex-wrap items-baseline gap-x-3 gap-y-1 border-b border-border-default px-8 py-6">
      <h1 className="h2-b text-fg-primary">{item.pageTitle}</h1>
      {item.pageDescription && (
        <p className="body-r text-fg-secondary">{item.pageDescription}</p>
      )}
    </header>
  );
}
