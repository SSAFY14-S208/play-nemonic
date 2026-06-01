import Image from 'next/image'

import { PhoneLauncherButton } from '@/shared/components'

const FLIPBOOK_RESULT_CONTROL_IMAGES = {
  howToPlay: '/images/flipbook-entrance-scene/how-to-play-button.png',
  soundOn: '/images/flipbook-entrance-scene/sound-on-button.png',
  soundMuted: '/images/flipbook-entrance-scene/sound-muted-button.png',
} as const

interface ResultTopControlsProps {
  isBgmMuted: boolean
  onOpenHowToPlay: () => void
  onToggleBgmMuted: () => void
}

export function ResultTopControls({
  isBgmMuted,
  onOpenHowToPlay,
  onToggleBgmMuted,
}: ResultTopControlsProps) {
  return (
    <div className="absolute left-4 top-[calc(env(safe-area-inset-top)+1rem)] z-[120] flex items-center gap-2 sm:left-6 sm:top-6">
      <ResultIconButton
        imageSrc={FLIPBOOK_RESULT_CONTROL_IMAGES.howToPlay}
        label="게임 설명"
        onClick={onOpenHowToPlay}
      />
      <ResultIconButton
        imageSrc={isBgmMuted ? FLIPBOOK_RESULT_CONTROL_IMAGES.soundMuted : FLIPBOOK_RESULT_CONTROL_IMAGES.soundOn}
        label={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
        pressed={isBgmMuted}
        onClick={onToggleBgmMuted}
      />
      <PhoneLauncherButton className="size-14 sm:size-[clamp(54px,4.6vw,70px)]" />
    </div>
  )
}

function ResultIconButton({
  imageSrc,
  label,
  pressed,
  onClick,
}: {
  imageSrc: string
  label: string
  pressed?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      title={label}
      className="relative grid size-14 place-items-center transition duration-150 hover:-translate-y-0.5 active:translate-y-px active:scale-95 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-flipbook-primary sm:size-[clamp(54px,4.6vw,70px)]"
      onClick={onClick}
    >
      <Image
        src={imageSrc}
        alt=""
        width={67}
        height={70}
        sizes="70px"
        className="h-full w-auto object-contain"
      />
      <span className="sr-only">{label}</span>
    </button>
  )
}
