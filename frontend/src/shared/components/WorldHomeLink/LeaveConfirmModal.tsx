'use client'

import { AnimatePresence, motion } from 'motion/react'

interface LeaveConfirmModalProps {
  open: boolean
  onCancel: () => void
  onConfirm: () => void
}

export default function LeaveConfirmModal({
  open,
  onCancel,
  onConfirm,
}: LeaveConfirmModalProps) {
  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="fixed inset-0 z-[var(--z-overlay)] flex items-center justify-center bg-black/45 px-4 backdrop-blur-sm"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onCancel}
          role="presentation"
        >
          <motion.div
            role="dialog"
            aria-modal
            aria-labelledby="leave-confirm-title"
            aria-describedby="leave-confirm-description"
            className="w-full max-w-[26rem] rounded-[var(--radius-xl)] bg-surface-default p-6 text-center shadow-lg"
            initial={{ y: 18, opacity: 0, scale: 0.95 }}
            animate={{ y: 0, opacity: 1, scale: 1 }}
            exit={{ y: 18, opacity: 0, scale: 0.95 }}
            transition={{ type: 'spring', stiffness: 360, damping: 26 }}
            onClick={(event) => event.stopPropagation()}
          >
            <h2
              id="leave-confirm-title"
              className="h3-b text-fg-primary"
            >
              네모닉 월드로 돌아갈까요?
            </h2>
            <p
              id="leave-confirm-description"
              className="body-r mt-2 text-fg-secondary"
            >
              지금까지 진행한 내용은 저장되지 않을 수 있어요.
            </p>
            <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
              <button
                type="button"
                className="body-l-b min-h-[2.75rem] flex-1 rounded-[var(--radius-md)] border border-border-default bg-surface-subtle px-4 text-fg-primary transition-colors hover:bg-surface-default sm:flex-none sm:px-6"
                onClick={onCancel}
              >
                계속 머물기
              </button>
              <button
                type="button"
                className="body-l-b min-h-[2.75rem] flex-1 rounded-[var(--radius-md)] bg-primary-1 px-4 text-fg-inverse transition-transform hover:scale-[1.02] sm:flex-none sm:px-6"
                onClick={onConfirm}
              >
                월드로 돌아가기
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
