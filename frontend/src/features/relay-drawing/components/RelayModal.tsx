'use client'

import { Dialog } from '@base-ui/react/dialog'
import { X } from 'lucide-react'
import { motion } from 'motion/react'

import { cn } from '@/shared/libs'

// 릴레이 드로잉 페이지 전용 모달 컨테이너.
// - 모든 모달 공통의 backdrop/popup 스타일과 등장 애니메이션을 제공.
// - `showHeader`가 true면 헤더(타이틀 + X 닫기 버튼)를 자동 렌더.
// - false면 dialog 접근성을 위해 title을 sr-only로 숨겨 둔다.
// - children에는 form/콘텐츠/푸터를 자유롭게 작성.
// - ESC/backdrop dismiss를 막아야 하는 강제 모달은 호출자가
//   `onOpenChange={() => {}}`를 넘기면 된다.

const POPUP_BASE_CLASS =
  'font-paperlogy fixed left-1/2 top-1/2 z-[var(--z-modal)] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-[var(--radius-xl)] bg-relay-paper shadow-lg'

const WIDTH_CLASS = {
  sm: 'w-[min(380px,calc(100vw-2rem))]',
  md: 'w-[min(420px,calc(100vw-2rem))]',
} as const

interface RelayModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  showHeader?: boolean
  width?: keyof typeof WIDTH_CLASS
  children: React.ReactNode
  className?: string
}

export default function RelayModal({
  open,
  onOpenChange,
  title,
  showHeader = true,
  width = 'md',
  children,
  className,
}: RelayModalProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-[var(--z-overlay)] bg-black/30" />
        <Dialog.Popup className={cn(POPUP_BASE_CLASS, WIDTH_CLASS[width], className)}>
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
          >
            {showHeader ? (
              <header className="flex items-center justify-between border-b border-relay-line px-5 py-4">
                <Dialog.Title className="h4-b text-relay-ink">{title}</Dialog.Title>
                <Dialog.Close
                  aria-label="닫기"
                  className="grid size-8 cursor-pointer place-items-center rounded-[var(--radius-md)] text-relay-muted transition-colors hover:bg-relay-active hover:text-relay-ink"
                >
                  <X className="size-4" />
                </Dialog.Close>
              </header>
            ) : (
              <Dialog.Title className="sr-only">{title}</Dialog.Title>
            )}
            {children}
          </motion.div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
