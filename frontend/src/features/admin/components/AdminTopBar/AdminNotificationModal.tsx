"use client";

import { Dialog } from "@base-ui/react/dialog";
import { X } from "lucide-react";

import { useNotificationStore } from "../../stores";

export function AdminNotificationModal() {
  const { notifications, isModalOpen, closeModal } = useNotificationStore();

  return (
    <Dialog.Root
      open={isModalOpen}
      onOpenChange={(open) => {
        if (!open) closeModal();
      }}
    >
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-[var(--z-overlay)] bg-black/30" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-[var(--z-modal)] w-[min(420px,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-[var(--radius-xl)] bg-surface-default shadow-lg">
          <header className="flex items-center justify-between border-b border-border-default px-5 py-4">
            <Dialog.Title className="h4-b text-fg-primary">
              알림 내역
            </Dialog.Title>
            <Dialog.Close
              aria-label="닫기"
              className="flex h-8 w-8 items-center justify-center rounded-[var(--radius-md)] text-fg-secondary transition-colors hover:bg-surface-subtle hover:text-fg-primary"
            >
              <X className="h-4 w-4" />
            </Dialog.Close>
          </header>

          <ul className="max-h-[60vh] divide-y divide-border-default overflow-y-auto">
            {notifications.length === 0 ? (
              <li className="body-r px-5 py-8 text-center text-fg-secondary">
                새로운 알림이 없습니다.
              </li>
            ) : (
              notifications.map((notification) => (
                <li
                  key={notification.id}
                  className="flex flex-col gap-1 px-5 py-4"
                >
                  <div className="flex items-baseline justify-between gap-3">
                    <p className="body-b text-fg-primary">
                      {notification.title}
                    </p>
                    <span className="caption-r shrink-0 text-fg-secondary">
                      {notification.receivedAt}
                    </span>
                  </div>
                  <p className="body-r text-fg-secondary">
                    {notification.body}
                  </p>
                </li>
              ))
            )}
          </ul>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
