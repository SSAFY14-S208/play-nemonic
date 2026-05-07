'use client'

import { Dialog } from '@base-ui/react/dialog'
import { LogOut, MonitorSmartphone } from 'lucide-react'

import type { RelayDismissalReason } from '../stores'

interface DismissalContent {
  Icon: typeof LogOut
  title: string
  description: string
}

const DISMISSAL_CONTENT: Record<RelayDismissalReason, DismissalContent> = {
  DUPLICATE_SESSION: {
    Icon: MonitorSmartphone,
    title: '다른 곳에서 접속했어요',
    description:
      '다른 기기 또는 탭에서 같은 방에 접속하여 이 세션이 종료되었습니다.',
  },
  ROOM_CLOSED: {
    Icon: LogOut,
    title: '방이 종료되었어요',
    description: '호스트가 방을 종료했습니다.',
  },
}

interface RelayDismissalModalProps {
  reason: RelayDismissalReason
  onConfirm: () => void
}

// 종료성 WS 이벤트(강퇴·중복 세션·방 종료) 수신 시 표시하는 안내 모달.
// 사용자가 "확인"을 누르면 clearRoom + 부스 이동이 수행된다.
// backdrop/ESC로 닫을 수 없다 — 반드시 확인 후 부스로 복귀.
export default function RelayDismissalModal({
  reason,
  onConfirm,
}: RelayDismissalModalProps) {
  const { Icon, title, description } = DISMISSAL_CONTENT[reason]

  return (
    <Dialog.Root open onOpenChange={() => {}}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-[var(--z-overlay)] bg-black/30" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-[var(--z-modal)] w-[min(380px,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-[var(--radius-xl)] bg-relay-paper shadow-lg">
          <div className="flex flex-col items-center gap-4 px-6 py-8 text-center">
            <div className="grid size-14 place-items-center rounded-full bg-relay-active">
              <Icon className="size-6 text-relay-accent-strong" aria-hidden />
            </div>

            <Dialog.Title className="h4-b text-relay-ink">
              {title}
            </Dialog.Title>

            <p className="body-r text-relay-muted">{description}</p>

            <button
              type="button"
              onClick={onConfirm}
              className="body-b mt-2 min-h-11 w-full rounded-[var(--radius-md)] bg-relay-accent px-5 text-relay-ink"
            >
              확인
            </button>
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
