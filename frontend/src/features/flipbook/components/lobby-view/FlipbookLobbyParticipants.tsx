'use client'

import Image from 'next/image'

export function ParticipantNameTag({
  name,
  avatar,
  isHost,
}: {
  name: string
  avatar: string
  isHost: boolean
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
