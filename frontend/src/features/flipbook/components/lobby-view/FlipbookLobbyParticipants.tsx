'use client'

import Image from 'next/image'
import { UserMinus } from 'lucide-react'

export function ParticipantNameTag({
  name,
  avatar,
  isHost,
  isConnected = false,
  canKick = false,
  onKick,
}: {
  name: string
  avatar: string
  isHost: boolean
  isConnected?: boolean
  canKick?: boolean
  onKick?: () => void
}) {
  return (
    <div className="flex min-h-16 items-center gap-3 rounded-[16px] border border-[#ffabb5] bg-[#fff1f1] px-4 shadow-[0_8px_16px_rgb(255_113_130_/_14%)] lg:min-h-[100px] lg:gap-5 lg:rounded-[18px] lg:px-7">
      <span className="grid size-11 shrink-0 place-items-center rounded-full bg-[#ffe5ad] text-[24px] shadow-[inset_0_0_0_3px_rgb(255_255_255_/_68%)] lg:size-16 lg:text-[34px]">
        {avatar}
      </span>
      <span className="h3-b min-w-0 flex-1 truncate text-[#684834]">{name}</span>
      {isHost && (
        <span className="caption-b rounded-full bg-[#ff7182] px-3 py-1.5 text-white lg:body-b lg:px-5 lg:py-2">
          방장
        </span>
      )}
      <span
        className="grid size-3 shrink-0 place-items-center rounded-full lg:size-4"
        style={{
          backgroundColor: isConnected ? '#78d08f' : '#d8b9ad',
        }}
        title={isConnected ? '연결됨' : '연결 대기'}
        aria-label={isConnected ? '연결됨' : '연결 대기'}
      />
      {canKick && (
        <button
          type="button"
          onClick={onKick}
          className="grid size-9 shrink-0 place-items-center rounded-full bg-white/80 text-[#cf5d68] shadow-[0_4px_10px_rgb(126_74_42_/_12%)] transition hover:-translate-y-0.5"
          aria-label={`${name} 강퇴`}
          title="강퇴"
        >
          <UserMinus className="size-4" aria-hidden />
        </button>
      )}
    </div>
  )
}

export function WaitingParticipantSlot({
  plusImageSrc,
}: {
  plusImageSrc: string
}) {
  return (
    <div className="flex min-h-16 items-center justify-center gap-3 rounded-[16px] border-2 border-dashed border-[#e7c6b6] bg-white/24 px-4 text-[#b49d91] lg:min-h-[100px] lg:gap-8 lg:rounded-[18px] lg:px-7">
      <Image
        src={plusImageSrc}
        alt=""
        width={39}
        height={39}
        className="size-7 lg:size-10"
      />
      <span className="body-b lg:body-l-b">참가 기다리는 중...</span>
    </div>
  )
}
