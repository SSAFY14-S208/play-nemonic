'use client'

import Image from 'next/image'

import { cn } from '@/shared/libs'

import { soundMutedIcon, soundOnIcon } from '@/features/relay-drawing/assets'
import { useRelayBgmStore } from '../stores'

// BGM mute 토글 버튼. relay-drawing 레이아웃 우상단에 floating으로 배치된다.
// 클릭 자체가 user gesture이므로, autoplay가 차단된 환경에서도 unmute 클릭
// 시점에 useRelayBgm 훅이 audio.play()를 다시 시도해 정상 재생된다.
export default function RelayBgmToggle({ className }: { className?: string }) {
  const isMuted = useRelayBgmStore((state) => state.isMuted)
  const toggleMuted = useRelayBgmStore((state) => state.toggleMuted)

  const label = isMuted ? '배경음 켜기' : '배경음 끄기'

  return (
    <button
      type="button"
      onClick={toggleMuted}
      aria-label={label}
      aria-pressed={!isMuted}
      title={label}
      // 아이콘 PNG 자체에 원형 디자인이 들어있어 배경/그림자/blur 없이 페이지
      // 배경 위에 그대로 얹는다. 클릭 시 미세한 시각 피드백만 hover/active로.
      className={cn(
        'inline-flex size-16 cursor-pointer items-center justify-center bg-transparent transition hover:-translate-y-0.5 hover:brightness-105 active:scale-95',
        className,
      )}
    >
      <Image
        src={isMuted ? soundMutedIcon : soundOnIcon}
        alt=""
        aria-hidden
        width={64}
        height={64}
        className="size-16 object-contain"
      />
    </button>
  )
}
