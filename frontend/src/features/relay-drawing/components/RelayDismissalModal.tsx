'use client'

import { LogOut, MonitorSmartphone } from 'lucide-react'

import type { RelayDismissalReason } from '../stores'

import RelayButton from './RelayButton'
import RelayModal from './RelayModal'

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
    <RelayModal
      open
      onOpenChange={() => {}}
      title={title}
      showHeader={false}
      width="sm"
    >
      <div className="flex flex-col items-center gap-4 px-6 py-8 text-center">
        <div className="grid size-14 place-items-center rounded-full bg-relay-active">
          <Icon className="size-6 text-relay-accent-strong" aria-hidden />
        </div>

        {/* RelayModal이 sr-only Dialog.Title을 이미 렌더하므로 시각적 표시는
            aria-hidden으로 두어 SR이 동일 문구를 두 번 읽지 않게 한다. */}
        <p className="h4-b text-relay-ink" aria-hidden>
          {title}
        </p>

        <p className="body-r text-relay-muted">{description}</p>

        <RelayButton
          variant="primary"
          onClick={onConfirm}
          className="mt-2 w-full"
        >
          확인
        </RelayButton>
      </div>
    </RelayModal>
  )
}
