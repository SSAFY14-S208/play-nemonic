import { PhoneLauncherButton } from '@/shared/components'
import { FlipbookDrawingIconButton } from '../DrawingViewControls'

interface DrawingTopControlsProps {
  isBgmMuted: boolean
  imageSources: {
    howToPlay: string
    soundOn: string
    soundMuted: string
  }
  onOpenHowToPlay: () => void
  onToggleBgmMuted: () => void
}

export function DrawingTopControls({
  isBgmMuted,
  imageSources,
  onOpenHowToPlay,
  onToggleBgmMuted,
}: DrawingTopControlsProps) {
  return (
    <div className="fixed right-4 top-4 z-[var(--z-sticky)] hidden items-center gap-2 lg:flex">
      <FlipbookDrawingIconButton
        imageSrc={imageSources.howToPlay}
        label="게임 설명"
        onClick={onOpenHowToPlay}
      />
      <FlipbookDrawingIconButton
        imageSrc={isBgmMuted ? imageSources.soundMuted : imageSources.soundOn}
        label={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
        pressed={isBgmMuted}
        onClick={onToggleBgmMuted}
      />
      <PhoneLauncherButton />
    </div>
  )
}
