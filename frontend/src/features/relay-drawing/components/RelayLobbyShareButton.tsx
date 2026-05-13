'use client'

import { createPortal } from 'react-dom'
import Image from 'next/image'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/shared/libs'
import {
  useRelayShareAction,
  type RelayShareActionKey,
} from '../hooks'

interface RelayLobbyShareButtonProps {
  actionKey: RelayShareActionKey
  label: string
  Icon: LucideIcon
  roomCode: string | null
  className?: string
}

export default function RelayLobbyShareButton({
  actionKey,
  label,
  Icon,
  roomCode,
  className,
}: RelayLobbyShareButtonProps) {
  const {
    feedbackLabel,
    qrCodeDataUrl,
    closeQrCode,
    runShareAction,
  } = useRelayShareAction({
    actionKey,
    label,
    roomCode,
  })

  return (
    <>
      <button
        type="button"
        onClick={runShareAction}
        disabled={!roomCode}
        className={cn(
          'body-b inline-flex min-h-11 cursor-pointer items-center justify-center gap-1.5 rounded-full border border-relay-line bg-relay-active px-3.5 text-relay-accent-strong transition-all hover:-translate-y-0.5 hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-55 disabled:hover:translate-y-0 disabled:hover:brightness-100',
          className,
        )}
      >
        <Icon className="size-[17px]" aria-hidden />
        {feedbackLabel}
      </button>

      {qrCodeDataUrl &&
        createPortal(
          <div
            className="fixed inset-0 z-50 grid place-items-center bg-relay-ink/30 px-5 backdrop-blur-[3px]"
            role="presentation"
            onMouseDown={(event) => {
              if (event.target === event.currentTarget) {
                closeQrCode()
              }
            }}
          >
            <div className="w-full max-w-[360px] rounded-[34px] border border-relay-line bg-relay-paper p-7 text-center text-relay-ink shadow-[0_24px_60px_rgb(75_52_38_/_24%)]">
              <p className="h3-b">QR 코드</p>
              <Image
                src={qrCodeDataUrl}
                alt="릴레이 드로잉 방 초대 QR 코드"
                width={256}
                height={256}
                unoptimized
                className="mx-auto mt-5 rounded-[18px] border border-relay-line bg-white p-3"
              />
              <p className="caption-m mt-4 text-relay-muted">
                친구가 스캔하면 바로 입장할 수 있어요.
              </p>
              <button
                type="button"
                onClick={closeQrCode}
                className="body-b mt-6 h-12 w-full rounded-full bg-relay-accent text-relay-ink shadow-[0_8px_18px_rgba(184,121,22,0.24)]"
              >
                닫기
              </button>
            </div>
          </div>,
          document.body,
        )}
    </>
  )
}
