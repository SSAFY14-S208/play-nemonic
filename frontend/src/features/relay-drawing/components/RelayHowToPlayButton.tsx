'use client'

import Image from 'next/image'

import { cn } from '@/shared/libs'

import helpIcon from '../assets/how-to-play.png'
import { useRelayHowToPlayStore } from '../stores'

// 게임 설명 모달 트리거 버튼. 모달 자체는 RelayHowToPlayModalHost가 단일
// 인스턴스로 마운트하고, 이 버튼은 zustand store의 open()만 호출한다.
// GameLobbyLayout이 mobile/desktop 헤더를 둘 다 마운트하기 때문에 버튼 인스턴스가
// 여러 개 떠도 모달은 1개만 떠야 한다.
export default function RelayHowToPlayButton({ className }: { className?: string }) {
  const open = useRelayHowToPlayStore((state) => state.open)

  return (
    <button
      type="button"
      onClick={open}
      aria-label="게임 설명 열기"
      title="게임 설명"
      className={cn(
        'inline-flex size-16 cursor-pointer items-center justify-center bg-transparent transition hover:-translate-y-0.5 hover:brightness-105 active:scale-95',
        className,
      )}
    >
      <Image
        src={helpIcon}
        alt=""
        aria-hidden
        width={64}
        height={64}
        className="size-16 object-contain"
      />
    </button>
  )
}
