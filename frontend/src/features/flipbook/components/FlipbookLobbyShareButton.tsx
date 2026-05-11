'use client'

import Image from 'next/image'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/shared/libs'
import {
  type FlipbookShareActionKey,
  useFlipbookShareAction,
} from '../hooks/useFlipbookShareAction'

interface FlipbookLobbyShareButtonProps {
  actionKey: FlipbookShareActionKey
  label: string
  Icon: LucideIcon
  roomCode: string | null
  images: {
    copyLinkButton: string
    qrCodeButton: string
  }
}

export default function FlipbookLobbyShareButton({
  actionKey,
  label,
  Icon,
  roomCode,
  images,
}: FlipbookLobbyShareButtonProps) {
  const {
    copyLabel,
    qrCodeDataUrl,
    closeQrCode,
    runShareAction,
  } = useFlipbookShareAction({
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
        aria-label={copyLabel}
        className={cn(
          'relative min-h-[64px] overflow-hidden rounded-[18px] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-55',
          actionKey === 'copyLink' ? 'aspect-[300/120]' : 'aspect-[149/60]',
        )}
      >
        <Image
          src={actionKey === 'copyLink' ? images.copyLinkButton : images.qrCodeButton}
          alt=""
          fill
          sizes="220px"
          unoptimized
          className="object-fill"
        />
        {copyLabel !== label && (
          <span className="caption-b absolute inset-0 grid place-items-center rounded-[18px] bg-white/72 text-[#684834]">
            {copyLabel}
          </span>
        )}
        <span className="sr-only">
          <Icon className="size-[17px]" aria-hidden />
          {copyLabel}
        </span>
      </button>

      {qrCodeDataUrl && (
        <div
          className="fixed inset-0 z-50 grid place-items-center bg-[#4b3426]/30 px-5 backdrop-blur-[3px]"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              closeQrCode()
            }
          }}
        >
          <div className="w-full max-w-[360px] rounded-[28px] border border-[#efd8c7] bg-[#fffaf3] p-7 text-center text-[#684834] shadow-[0_24px_60px_rgb(75_52_38_/_24%)]">
            <p className="h3-b">QR 코드</p>
            <Image
              src={qrCodeDataUrl}
              alt="플립북 방 초대 QR 코드"
              width={256}
              height={256}
              unoptimized
              className="mx-auto mt-5 rounded-[18px] border border-[#efd8c7] bg-white p-3"
            />
            <p className="caption-m mt-4 text-[#9a7f6d]">
              친구가 스캔하면 바로 입장할 수 있어요.
            </p>
            <button
              type="button"
              onClick={closeQrCode}
              className="body-b mt-6 h-12 w-full rounded-full bg-[#ff7182] text-white shadow-[0_8px_18px_rgb(255_113_130_/_24%)]"
            >
              닫기
            </button>
          </div>
        </div>
      )}
    </>
  )
}
