'use client'

import Image from 'next/image'

import { cn } from '@/shared/libs'

import soundMutedIcon from '../assets/sound-muted.png'
import soundOnIcon from '../assets/sound-on.png'
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
      className={cn(
        'grid size-12 cursor-pointer place-items-center rounded-full bg-white/80 shadow-[0_4px_12px_rgb(129_89_54_/_14%)] backdrop-blur-sm transition hover:bg-white',
        className,
      )}
    >
      <Image
        src={isMuted ? soundMutedIcon : soundOnIcon}
        alt=""
        aria-hidden
        width={32}
        height={32}
        className="size-8 object-contain"
      />
    </button>
  )
}
